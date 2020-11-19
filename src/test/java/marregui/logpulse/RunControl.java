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

import marregui.logpulse.clf.CLF;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class RunControl implements PeriodicSchedule<CLF> {

    private final String name;
    private final int periodSecs;
    private final CountDownLatch alarmToGoOff;
    private final CountDownLatch readComplete;
    private final long alarmBytesThreshold;
    private int linesRead;
    private long bytesRead;
    private boolean alarmBytesThresholdCrossed;
    private long lastSeenTimestamp;

    public RunControl(String name, int periodSecs, int expectedLogLines, long alarmBytesThreshold) {
        this.name = name;
        this.periodSecs = periodSecs;
        readComplete = new CountDownLatch(expectedLogLines);
        this.alarmBytesThreshold = alarmBytesThreshold;
        alarmToGoOff = new CountDownLatch(1);
    }

    public long getBytesRead() {
        return bytesRead;
    }

    public long getLinesRead() {
        return linesRead;
    }

    public void awaitAlarmToGoOff() throws InterruptedException {
        alarmToGoOff.await();
    }

    public void awaitReadComplete() throws InterruptedException {
        readComplete.await();
    }

    @Override
    public String getName() {
        return RunControl.class.getSimpleName() + "_" + name;
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
        int size = events.size();
        if (size > 0) {
            lastSeenTimestamp = periodEnd;
            long s = events.get(0).getUTCTimestamp();
            long e = events.get(size - 1).getUTCTimestamp();
            int t = (int) ((e - s) / 1000L) + 1;
            assertThat(t, lessThanOrEqualTo(periodSecs));
            linesRead += size;
            bytesRead += events.stream()
                    .map(clf -> Long.valueOf(clf.getBytes()))
                    .reduce((a, b) -> Long.valueOf(a + b)).orElse(Long.valueOf(0L));
            if (!alarmBytesThresholdCrossed && bytesRead >= alarmBytesThreshold) {
                alarmBytesThresholdCrossed = true;
                alarmToGoOff.countDown();
            }
            for (int i = 0; i < size; i++) {
                readComplete.countDown();
            }
        }
    }
}
