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

package marregui.logpulse.clf.stats;

import marregui.logpulse.PeriodicSchedule;
import marregui.logpulse.clf.CLF;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import marregui.logpulse.clf.CLF.HTTPMethod;

/**
 * Model class for tracking general statistics over a period
 * defined in seconds.
 */
public class GeneralStats implements PeriodicSchedule<CLF> {

    /**
     * Default period 10 seconds
     */
    public static final int DEFAULT_PERIOD_SECS = 10;


    private long startTs;
    private long endTs;
    private long logsCount;
    private long inBytes;
    private long outBytes;
    private final int periodSecs;
    private final Map<String, AtomicLong> perSectionCount;
    private final Map<HTTPMethod, AtomicLong> perMethodCount;
    private final Map<String, AtomicLong> perVersionCount;
    private final Map<StatusCategory, AtomicLong> perStatusCategoryCount;

    /**
     * Constructor. Period defaults to 10 seconds.
     */
    public GeneralStats() {
        this(DEFAULT_PERIOD_SECS);
    }

    /**
     * Constructor. Period is provided.
     *
     * @param periodSecs period in seconds
     */
    public GeneralStats(int periodSecs) {
        this.periodSecs = periodSecs;
        perSectionCount = new HashMap<>();
        perMethodCount = new HashMap<>();
        perVersionCount = new HashMap<>();
        perStatusCategoryCount = new HashMap<>();
    }

    @Override
    public  int getPeriodSecs() {
        return periodSecs;
    }

    @Override
    public String getName() {
        return "General HTTP Traffic Statistics";
    }

    @Override
    public void executeSchedule(long periodStart, long periodEnd, List<CLF> events) {
        reset();
        if (events.isEmpty()) {
            startTs = periodStart;
            endTs = periodEnd;
        }
        for (CLF log : events) {
            long ts = log.getUTCTimestamp();
            if (startTs == 0L) {
                startTs = ts;
            }
            if (endTs == 0L) {
                endTs = periodEnd;
            }
            incrementCounter(perSectionCount, log.getSection());
            incrementCounter(perVersionCount, log.getVersion());
            incrementCounter(perStatusCategoryCount, StatusCategory.valueOf(log.getStatus()));
            incrementCounter(perMethodCount, log.getMethod());
            switch (log.getMethod()) {
                case GET, HEAD, OPTIONS, DELETE -> outBytes += log.getBytes();
                case PUT, POST, PATCH -> inBytes += log.getBytes();
            }
            logsCount++;
        }
    }

    /**
     * Returns the UTC Epoch representing the start of the period being reported.
     *
     * @return UTC Epoch representing the start of the period being reported
     */
    public long getStartTs() {
        return startTs;
    }

    /**
     * The UTC Epoch representing the end of the period being reported
     * @return UTC Epoch representing the end of the period being reported
     */
    public long getLastSeenUTCTimestamp() {
        return endTs;
    }

    /**
     * The number of logs received during the period.
     * @return number of logs received during the period
     */
    public long getLogsCount() {
        return logsCount;
    }

    /**
     * Returns a collection of Map.Entry entries, where the key represents
     * a particular HTTP method, and the value its hit count.
     * @return a collection containing Map.Entry entries, where the
     * key represents a particular HTTP method, and the value its hit count
     */
    public Collection<Map.Entry<?, AtomicLong>> getPerMethodCount() {
        return new ArrayList<>(perMethodCount.entrySet());
    }

    /**
     * Returns a collection of Map.Entry entries, where the key represents
     * a particular section, and the value its hit count.
     * @return a collection of Map.Entry entries, where the key represents
     * a particular section, and the value its hit count
     */
    public Collection<Map.Entry<?, AtomicLong>> getPerSectionCount() {
        return new ArrayList<>(perSectionCount.entrySet());
    }

    /**
     * Returns a collection of Map.Entry entries, where the key represents
     * a particular HTTP version, and the value its hit count.
     * @return a collection of Map.Entry entries, where the key represents
     * a particular HTTP version, and the value its hit count
     */
    public Collection<Map.Entry<?, AtomicLong>> getPerVersionCount() {
        return new ArrayList<>(perVersionCount.entrySet());
    }

    /**
     * Returns a collection of Map.Entry entries, where the key represents
     * a particular category for values of the status of the requests, and
     * the value its hit count.
     * @return a collection of Map.Entry entries, where the key represents
     * a particular category for values of the status of the requests, and
     * the value its hit count
     */
    public Collection<Map.Entry<?, AtomicLong>> getPerStatusCategoryCount() {
        return new ArrayList<>(perStatusCategoryCount.entrySet());
    }

    /**
     * Return the number of bytes received from the client.
     * @return number of bytes received from the client
     */
    public long getInBytes() {
        return inBytes;
    }

    /**
     * Return the number of bytes sent to the client.
     * @return number of bytes sent to the client
     */
    public long getOutBytes() {
        return outBytes;
    }

    /**
     * Resets all stats to start values (0L, empty).
     */
    public void reset() {
        inBytes = 0L;
        outBytes = 0L;
        logsCount = 0L;
        startTs = 0L;
        endTs = 0L;
        perSectionCount.clear();
        perMethodCount.clear();
        perVersionCount.clear();
        perStatusCategoryCount.clear();
    }

    private static <T> void incrementCounter(Map<T, AtomicLong> map, T key) {
        if (key != null) {
            map.putIfAbsent(key, new AtomicLong(0L));
            map.get(key).incrementAndGet();
        }
    }

    /**
     * Represents categories for the status value of a request.
     */
    public enum StatusCategory {
        /**
         * 1xx
         */
        InformationResponse(100, 200),
        /**
         * 2xx
         */
        Success(200, 300),
        /**
         * 3xx
         */
        Redirection(300, 400),
        /**
         * 4xx
         */
        ClientError(400, 500),
        /**
         * 5xx
         */
        ServerError(500, 600);

        private final int start;
        private final int end;

        StatusCategory(int start, int end) {
            this.start = start;
            this.end = end;
        }

        /**
         * Returns the category the status value belongs to.
         * @param status status value for the request
         * @return the category the status value belongs to
         */
        public static StatusCategory valueOf(int status) {
            for (StatusCategory cat : values()) {
                if (status >= cat.start && status < cat.end) {
                    return cat;
                }
            }
            throw new IllegalArgumentException("status out of bounds: " + status);
        }
    }
}
