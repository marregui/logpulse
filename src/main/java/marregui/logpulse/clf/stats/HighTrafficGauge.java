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
import marregui.logpulse.UTCTimestamp;
import marregui.logpulse.clf.CLF;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;

/**
 * Reports a message when the average throughput (requests per second) over a period
 * is greater than a predefined threshold, on average, and then it reports a further
 * message if throughput crosses the threshold back in the other direction.
 */
public class HighTrafficGauge implements PeriodicSchedule<CLF> {

    /**
     * Default period is 120 seconds.
     */
    public static final int DEFAULT_PERIOD_SECS = 120;

    /**
     * Default threshold is 10 requests per second.
     */
    public static final double DEFAULT_REQUESTS_PER_SECOND_THRESHOLD = 10.0;


    private final PrintStream printWriter;
    private final int periodSecs;
    private volatile double requestsPerSecOnAvgThreshold;
    private boolean thresholdCrossed;
    private long lastSeenTimestamp;

    /**
     * Constructor, using defaults (period: 120 seconds, threshold: 10 req per sec).
     * @param printWriter used to print a report when the threshold is crossed
     */
    public HighTrafficGauge(PrintStream printWriter) {
        this(printWriter, DEFAULT_PERIOD_SECS, DEFAULT_REQUESTS_PER_SECOND_THRESHOLD);
    }

    /**
     * Constructor.
     * @param printWriter will print a report periodically
     * @param periodSecs period in seconds
     * @param requestsPerSecOnAvgThreshold threshold average requests per second
     */
    public HighTrafficGauge(PrintStream printWriter, int periodSecs, double requestsPerSecOnAvgThreshold) {
        this.printWriter = Objects.requireNonNull(printWriter);
        this.periodSecs = periodSecs;
        this.requestsPerSecOnAvgThreshold = requestsPerSecOnAvgThreshold;
    }

    /**
     * @param requestsPerSecOnAvgThreshold sets the threshold
     */
    public void setThreshold(double requestsPerSecOnAvgThreshold) {
        this.requestsPerSecOnAvgThreshold = requestsPerSecOnAvgThreshold;
    }

    /**
     * @return the threshold
     */
    public double getThreshold() {
        return requestsPerSecOnAvgThreshold;
    }

    @Override
    public String getName() {
        return String.format("High Traffic Gauge (%.2f req. per sec.)", requestsPerSecOnAvgThreshold);
    }

    @Override
    public int getPeriodSecs() {
        return periodSecs;
    }

    @Override
    public long getLastSeenUTCTimestamp() {
        return lastSeenTimestamp;
    }

    @Override
    public void executeSchedule(long periodStart, long periodEnd, List<CLF> events) {
        lastSeenTimestamp = periodEnd;
        if (events.isEmpty()) {
            return;
        }
        long nextSecBoundary = UTCTimestamp.truncateMillis(events.get(0).getUTCTimestamp()) + 1000L;
        int grpIdx = 0;
        int hitsPerSec = 0;
        int sumHits = 0;
        for (CLF event : events) {
            long ts = UTCTimestamp.truncateMillis(event.getUTCTimestamp());
            if (ts >= nextSecBoundary) {
                sumHits += hitsPerSec;
                double avgReqPerSec = sumHits / (grpIdx + 1.0);
                if (avgReqPerSec > requestsPerSecOnAvgThreshold && !thresholdCrossed) {
                    thresholdCrossed = true;
                    int offendingIdx = sumHits - hitsPerSec + (int) Math.floor(requestsPerSecOnAvgThreshold);
                    long offendingTs = events.get(offendingIdx).getUTCTimestamp();
                    printWriter.print(buildReport(
                            "High Traffic",
                            offendingIdx, avgReqPerSec, offendingTs));
                }
                if (avgReqPerSec < requestsPerSecOnAvgThreshold && thresholdCrossed) {
                    thresholdCrossed = false;
                    long offendingTs = events.get(sumHits).getUTCTimestamp();
                    printWriter.print(buildReport(
                            "Traffic is back to normal",
                            sumHits, avgReqPerSec, offendingTs));
                }
                grpIdx++;
                hitsPerSec = 0;
                nextSecBoundary = ts + 1000L;
            }
            hitsPerSec++;
        }
    }

    private String buildReport(String message, int hits, double avg, long ts) {
        return String.format("%s: %s - hits = {%d}, avg: %.2f, triggered: {%s}%n",
                getName(), message, hits, avg, UTCTimestamp.formatForDisplay(ts));
    }
}
