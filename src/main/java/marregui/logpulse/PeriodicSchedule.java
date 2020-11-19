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

import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Interface defining the contract for an instance to be a periodic
 * schedule.
 * <p>
 * A periodic schedule is executed every period seconds, and given the
 * start and end UTC Epochs defining the period, as well as the list of
 * events observed during the period.
 *
 * @param <T> a class implementing {@link WithUTCTimestamp}
 * @see WithUTCTimestamp
 */
public interface PeriodicSchedule<T extends WithUTCTimestamp> {

    /**
     * @return name to identify the schedule
     */
    String getName();

    /**
     * @return period in seconds to trigger the schedule, defaults to 10
     */
    default int getPeriodSecs() {
        return 10;
    }

    /**
     * @param ticks a UTC  Epoch representing the concept of application current tick time
     * @return true if the period is &gt; 0 and <code>'ticks % periodSecs == 0L'</code>
     */
    default boolean isInSchedule(long ticks) {
        return isInSchedule(ticks, getPeriodSecs());
    }

    /**
     * @param periodStart UTC Epoch representing the start of the period
     * @param periodEnd   UTC Epoch representing the end of the period
     * @param events      list of events associated with the trigger
     */
    void executeSchedule(long periodStart, long periodEnd, List<T> events);

    /**
     * This value is used to assist cache eviction and period boundary detection.
     * <p>
     * Return 0L when no last seen timestamp exists
     * @return last timestamp seen by method {@linkplain #executeSchedule(long, long, List)}, or 0L
     *
     */
    long getLastSeenUTCTimestamp();


    /**
     * @param schedules a collection of alerts
     * @param <T>    a class implementing {@link WithUTCTimestamp}
     * @return the schedule of highest period within the collection
     */
    static <T extends WithUTCTimestamp> PeriodicSchedule<T> scheduleOfLongestPeriod(Collection<PeriodicSchedule<T>> schedules) {
        return schedules != null ? schedules.stream()
                .reduce((s1, s2) -> s1.getPeriodSecs() > s2.getPeriodSecs() ? s1 : s2)
                .orElse(null) : null; // to please the compiler
    }

    /**
     * @param schedules a collection of schedules
     * @param ticks  UTC Epoch representing the current application's tick time
     * @param <T>    a class implementing {@link WithUTCTimestamp}
     * @return the number of elements in the collection for which it
     * is trigger time ({@link PeriodicSchedule#isInSchedule(long, long)} == true).
     */
    static <T extends WithUTCTimestamp> int readyCount(Collection<PeriodicSchedule<T>> schedules, long ticks) {
        return (int) periodStream(schedules).filter(s -> isInSchedule(ticks, s)).count();
    }

    /**
     * @param ticks      UTC  Epoch representing the current application's tick time
     * @param periodSecs period in seconds when the schedule is executed
     * @return true if the period is &gt; 0 and <code>'ticks % periodSecs == 0L'</code>
     */
    static boolean isInSchedule(long ticks, long periodSecs) {
        return periodSecs > 0L && ticks % periodSecs == 0L;
    }

    private static <T extends WithUTCTimestamp> IntStream periodStream(Collection<PeriodicSchedule<T>> schedules) {
        return schedules.stream().mapToInt(PeriodicSchedule::getPeriodSecs);
    }
}
