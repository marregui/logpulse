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

import marregui.logpulse.UTCTimestamp;
import marregui.logpulse.clf.CLF;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.lang.Math.log;

/**
 * View class for {@link GeneralStats}, reporting periodically
 * general statistics.
 */
public class GeneralStatsView extends GeneralStats {

    private static final int MAX_LINES_PER_STAT = 10;
    private static final String LINE_END = System.lineSeparator();


    private final PrintStream printWriter;

    /**
     * Constructor.
     *
     * @param printWriter used to print a snapshot periodically
     */
    public GeneralStatsView(PrintStream printWriter) {
        super();
        this.printWriter = Objects.requireNonNull(printWriter);
    }

    /**
     * Constructor. Period is provided.
     *
     * @param printWriter used to print a snapshot periodically
     * @param periodSecs  period in seconds
     */
    public GeneralStatsView(PrintStream printWriter, int periodSecs) {
        super(periodSecs);
        this.printWriter = Objects.requireNonNull(printWriter);
    }

    @Override
    public void executeSchedule(long periodStart, long periodEnd, List<CLF> events) {
        super.executeSchedule(periodStart, periodEnd, events);
        printWriter.print(buildSnapshot(new StringBuilder()));
    }

    private String buildSnapshot(StringBuilder sb) {
        appendHeader(sb);
        append(sb, "Count per section:", getPerSectionCount());
        append(sb, "Count per method:", getPerMethodCount());
        append(sb, "Count per version:", getPerVersionCount());
        append(sb, "Count per status category:", getPerStatusCategoryCount());
        long in = getInBytes();
        long out = getOutBytes();
        append(sb, String.format(
                "Total received (%s): ",
                commaSeparated(
                        CLF.HTTPMethod.POST,
                        CLF.HTTPMethod.PUT)), in);
        append(sb, String.format(
                "Total sent (%s): ",
                commaSeparated(
                        CLF.HTTPMethod.GET,
                        CLF.HTTPMethod.HEAD,
                        CLF.HTTPMethod.PATCH,
                        CLF.HTTPMethod.OPTIONS,
                        CLF.HTTPMethod.DELETE)), out);
        append(sb, "Total IO: ", in + out);
        return sb.toString();
    }

    private static String commaSeparated(CLF.HTTPMethod... methods) {
        return Arrays.stream(methods)
                .map(CLF.HTTPMethod::name)
                .collect(Collectors.joining(", "));
    }

    private void appendHeader(StringBuilder sb) {
        sb.append(getName()).append(LINE_END);
        sb.append("=".repeat(getName().length())).append(LINE_END);
        sb.append("Period: ").append(getPeriodSecs()).append(" seconds").append(LINE_END);
        sb.append("From: ").append(UTCTimestamp.formatForDisplay(getStartTs())).append(LINE_END);
        sb.append("To: ").append(UTCTimestamp.formatForDisplay(getLastSeenUTCTimestamp())).append(LINE_END);
        long count = getLogsCount();
        sb.append("Count: ").append(count).append(LINE_END);
        sb.append(String.format("Logs per second: %.2f", 1.0 * count / getPeriodSecs())).append(LINE_END);
    }

    private static void append(StringBuilder sb, String title, Collection<Map.Entry<?, AtomicLong>> entries) {
        if (!entries.isEmpty()) {
            sb.append(title).append(LINE_END);
            sb.append(valueSortedRepresentation(entries)).append(LINE_END);
        }
    }

    private void append(StringBuilder sb, String title, long size) {
        sb.append(title).append(toHumanReadableSize(size))
                .append(String.format(" (%sps)", toHumanReadableSize(1.0 * size / getPeriodSecs())))
                .append(LINE_END);
    }

    private static String valueSortedRepresentation(Collection<Map.Entry<?, AtomicLong>> entries) {
        return entries.isEmpty() ? null : entries
                .stream()
                .sorted(Comparator
                        .comparingLong((Map.Entry<?, AtomicLong> e) -> e.getValue().get())
                        .thenComparing(e -> e.getKey().toString())
                        .reversed())
                .map(e -> String.format(" - %s: %s", e.getKey(), e.getValue()))
                .limit(MAX_LINES_PER_STAT)
                .collect(Collectors.joining(LINE_END));
    }

    private static final char[] SCALE = "BKMGTP".toCharArray();

    private static String toHumanReadableSize(double byteCount) {
        if (byteCount < 1024.0) {
            return String.format("%.2fB", byteCount);
        }
        int step = (int) (log(byteCount) / log(2)) / 10;
        double size = byteCount / (2 << (10 * step - 1));
        return String.format("%.2f%cB", size, SCALE[step]);
    }
}
