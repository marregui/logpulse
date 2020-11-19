/* **
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright 2020, Miguel Arregui a.k.a. marregui
 */
package marregui.logpulse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Processes periodic schedules on demand by the Scheduler. On schedule time,
 * the schedules are given a list of events from a readout cache, which is
 * accessed for read and eviction.
 * <p>
 * Schedules whose periods are multiples of each other are executed in order
 * of increasing period.<p>
 * When the
 *
 * @param <T> a class implementing {@link WithUTCTimestamp}
 * @see WithUTCTimestamp
 * @see ReadoutCache
 * @see PeriodicSchedule
 * @see Scheduler
 */
public class SchedulesProcessor<T extends WithUTCTimestamp> extends TaskProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulesProcessor.class);

    private final ReadoutCache<T> readoutCache;
    private final Lock schedulesLock;
    private final List<PeriodicSchedule<T>> schedules;
    private long lastEvictTick;

    /**
     * Constructor.
     *
     * @param numThreads        number of threads to process the schedules. Use 1 to
     *                          serialize the execution of schedules. Use more than one
     *                          to execute schedules concurrently
     * @param readoutCache      cache holding events, populated externally by some
     *                          thread other than the threads managed by this processor
     * @param threadsAreDaemons flag to determine whether the threading model
     *                          uses standard threads, or daemon threads
     */
    public SchedulesProcessor(int numThreads, ReadoutCache<T> readoutCache, boolean threadsAreDaemons) {
        super(numThreads, threadsAreDaemons);
        this.readoutCache = readoutCache;
        schedulesLock = new ReentrantLock();
        schedules = new ArrayList<>();
        lastEvictTick = -1L;
    }

    /**
     * Sets the periodic schedule, that it is triggered every period seconds,
     * and given the start and end UTC Epochs defining the period, along
     * with the list of events for that period.
     *
     * @param schedule a schedule
     */
    public void setPeriodicSchedule(PeriodicSchedule<T> schedule) {
        if (schedule.getPeriodSecs() <= 0) {
            LOGGER.error("Bluntly ignoring schedule {} with period {} secs",
                    schedule.getName(), Integer.valueOf(schedule.getPeriodSecs()));
        }
        LOGGER.info("Schedule set: {}, period secs: {}",
                schedule.getName(), Integer.valueOf(schedule.getPeriodSecs()));
        schedulesLock.lock();
        try {
            schedules.add(schedule);
            schedules.sort(PeriodicSchedule.COMPARING);
        } finally {
            schedulesLock.unlock();
        }
    }

    /**
     * Processes the schedules that are trigger ready, and potentially
     * evicts the readout cache.
     *
     * @param ticks current application's tick count, equivalent to the
     *              concept of Epoch, however the application's ticks
     *              are incremented by one every second (as measured by
     *              the application)
     */
    public void processSchedules(long ticks) {
        if (!isRunning()) {
            throw new IllegalStateException("not running");
        }
        schedulesLock.lock();
        try {
            int readyCount = PeriodicSchedule.readyCount(schedules, ticks);
            if (readyCount == 0) {
                return;
            }
            PeriodicSchedule<T> cacheEvictingSchedule = PeriodicSchedule.scheduleOfLongestPeriod(schedules);
            boolean cacheHasData = !readoutCache.isEmpty();
            boolean isEvictTick = cacheHasData && PeriodicSchedule.isInSchedule(ticks, cacheEvictingSchedule.getPeriodSecs());
            if (isEvictTick) {
                lastEvictTick = ticks;
            }
            if (cacheHasData) {
                long headTs = readoutCache.firstTimestamp();
                LOGGER.debug("Ready count: {}, tick: {}, evictPeriodSecs: {}, evict: {}, timestamp at head of cache: {}",
                        Integer.valueOf(readyCount),
                        Long.valueOf(ticks),
                        Integer.valueOf(cacheEvictingSchedule.getPeriodSecs()),
                        Boolean.valueOf(isEvictTick),
                        UTCTimestamp.formatForDisplay(headTs));
            }
            for (PeriodicSchedule<T> schedule : schedules) {
                if (schedule.isInSchedule(ticks)) {
                    long startTs = schedulePeriodStart(schedule,
                            schedule == cacheEvictingSchedule,
                            lastEvictTick + 1 == ticks);
                    long endTs = schedulePeriodEnd(schedule, startTs);
                    List<T> scheduleEvents = startTs != ReadoutCache.NO_VALUE ?
                            readoutCache.fetch(startTs, endTs) : Collections.emptyList();
                    logSchedule(schedule, startTs, endTs, scheduleEvents, ticks);
                    if (!isRunning()) {
                        throw new IllegalStateException("not running");
                    }
                    processTask(() -> {
                        long now = System.currentTimeMillis();
                        long s = startTs != ReadoutCache.NO_VALUE ? startTs : now;
                        long e = endTs != ReadoutCache.NO_VALUE ? endTs : now;
                        schedule.executeSchedule(s, e, scheduleEvents);
                        if (isEvictTick && schedule == cacheEvictingSchedule) {
                            readoutCache.evict(scheduleEvents.size());
                        }
                    });
                }
            }
        } finally {
            schedulesLock.unlock();
        }
    }

    private long schedulePeriodStart(PeriodicSchedule<T> schedule,
                                     boolean isCacheEvicting,
                                     boolean isFirstTickAfterEviction) {
        long startTs;
        long lastSeenTs = schedule.getLastSeenUTCTimestamp();
        if (isCacheEvicting || lastSeenTs == 0L || isFirstTickAfterEviction) {
            startTs = readoutCache.firstTimestamp();
        } else {
            startTs = readoutCache.firstTimestampSince(lastSeenTs);
        }
        return startTs;
    }

    private static <T extends WithUTCTimestamp> long schedulePeriodEnd(PeriodicSchedule<T> schedule, long startTs) {
        return startTs != ReadoutCache.NO_VALUE ?
                startTs + (schedule.getPeriodSecs() - 1) * 1000L
                :
                ReadoutCache.NO_VALUE;
    }

    private static <T extends WithUTCTimestamp> void logSchedule(PeriodicSchedule<T> schedule, long startTs, long endTs, List<T> events, long ticks) {
        if (!events.isEmpty()) {
            LOGGER.debug("!! Schedule: {}, event count: {}, tick: {}, start: {}, end: {}",
                    schedule.getName(),
                    Integer.valueOf(events.size()),
                    Long.valueOf(ticks),
                    UTCTimestamp.formatForDisplay(startTs),
                    UTCTimestamp.formatForDisplay(endTs));
        } else {
            long now = System.currentTimeMillis();
            long s = startTs != ReadoutCache.NO_VALUE ? startTs : now;
            long e = endTs != ReadoutCache.NO_VALUE ? endTs : now;
            LOGGER.debug("!! Schedule: {}, tick: {}, event count: {}, start: {}, end: {}",
                    schedule.getName(),
                    Long.valueOf(ticks),
                    Integer.valueOf(events.size()),
                    UTCTimestamp.formatForDisplay(s),
                    UTCTimestamp.formatForDisplay(e));
        }
    }
}
