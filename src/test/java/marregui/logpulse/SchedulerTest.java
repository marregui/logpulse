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
import marregui.logpulse.clf.CLFReadoutHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

public class SchedulerTest {

    @Test
    public void test_watcher_wont_start_if_parent_folder_does_not_exist() {
        Path file = Store.resolve(Store.nestedAccessLogFileName());
        try {
            new Scheduler<>(new CLFReadoutHandler(file)).start();
            fail("should not happen");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("cannot access parent folder: " + file.getParent()));
        }
    }

    @Test
    public void test_watcher_cannot_be_started_twice() throws IOException {
        Path file = Store.resolve(Store.nestedAccessLogFileName());
        Scheduler<CLF> watcher = new Scheduler<>(new CLFReadoutHandler(file));
        Files.createDirectories(file.getParent());
        try {
            watcher.start();
            watcher.start();
            fail("should not happen");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("already running"));
        } finally {
            watcher.stop();
            Files.deleteIfExists(file.getParent());
        }
    }

    @Test
    public void test_watcher_cannot_be_stopped_if_not_running() {
        Path file = Store.resolve(Store.nestedAccessLogFileName());
        Scheduler<CLF> watcher = new Scheduler<>(new CLFReadoutHandler(file));
        try {
            watcher.stop();
            fail("should not happen");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("not running"));
        }
    }

    @Test
    public void test_watcher_start_stop_is_running() {
        Path file = Store.resolve(Store.accessLogFileName());
        Scheduler<CLF> watcher = new Scheduler<>(new CLFReadoutHandler(file));
        assertThat(watcher.watchedFolder(), is(file.getParent()));
        assertThat(watcher.isRunning(), is(false));
        watcher.start();
        assertThat(watcher.isRunning(), is(true));
        watcher.stop();
        assertThat(watcher.isRunning(), is(false));
    }

    @Test
    public void test_watcher_can_be_joined() throws IOException {
        Path file = Store.resolve(Store.nestedAccessLogFileName());
        Files.createDirectories(file.getParent());
        try {
            Scheduler<CLF> watcher = new Scheduler<>(new CLFReadoutHandler(file));
            watcher.start();
            CompletableFuture.runAsync(() -> {
                contextSwitch(80L);
                watcher.stop();
            });
            long start = System.currentTimeMillis();
            while (watcher.joinTasks(100L)) {
                watcher.stop();
            }
            assertThat(System.currentTimeMillis() - start, lessThan(300L));
            assertThat(watcher.joinTasks(0L), is(false));
        } finally {
            Files.deleteIfExists(file.getParent());
        }
    }

    @Test
    public void test_watcher_finishes_gracefully_when_parent_folder_is_deleted() throws IOException {
        Path file = Store.resolve(Store.nestedAccessLogFileName());
        Files.createDirectories(file.getParent());
        try {
            Scheduler<CLF> watcher = new Scheduler<>(new CLFReadoutHandler(file));
            watcher.start();
            CompletableFuture.runAsync(() -> {
                contextSwitch(100L);
                try {
                    Files.delete(file.getParent());
                } catch (IOException e) {
                    fail("should not happen");
                }
            });
            while (watcher.joinTasks(100L)) {
                contextSwitch(1L);
            }
            assertThat(watcher.isRunning(), is(false));
            assertThat(watcher.joinTasks(0L), is(false));
            assertThat(Files.exists(file.getParent()), is(false));
        } finally {
            Files.deleteIfExists(file.getParent());
        }
    }

    @Test
    public void test_watcher_with_an_alert_of_period_one_sec() throws IOException, InterruptedException {
        long window = 2_000L;
        long delta = 200L;
        int expectedLogLinesCount = (int) (window / delta);
        long alarmBytesThreshold = 300L;
        String fileName = Store.accessLogFileName();
        Path file = Store.resolve(fileName);
        Scheduler<CLF> watcher = new Scheduler<>(new CLFReadoutHandler(file));
        RunControl runControl = new RunControl("rc1",1, expectedLogLinesCount, alarmBytesThreshold);
        watcher.setPeriodicSchedule(runControl);
        watcher.start();
        try {
            Store.storeToFile(file, new CLFGenerator(System.currentTimeMillis(), window, delta), false);
            runControl.awaitAlarmToGoOff();
            runControl.awaitReadComplete();
        } finally {
            watcher.stop();
            Files.deleteIfExists(file);
            assertThat(runControl.getBytesRead(), greaterThan(alarmBytesThreshold));
        }
    }

    @Test
    public void test_watcher_with_two_alerts() throws IOException, InterruptedException {
        long window = 2_000L;
        long delta = 200L;
        int expectedLogLinesCount = (int) (window / delta);
        long alarmBytesThreshold = 300L;
        String fileName = Store.accessLogFileName();
        Path file = Store.resolve(fileName);
        Scheduler<CLF> watcher = new Scheduler<>(new CLFReadoutHandler(file));
        RunControl runControl1 = new RunControl("rc1", 1, expectedLogLinesCount, alarmBytesThreshold);
        RunControl runControl3 = new RunControl("rc3", 3, expectedLogLinesCount, alarmBytesThreshold);
        watcher.setPeriodicSchedule(runControl1);
        watcher.setPeriodicSchedule(runControl3);
        watcher.start();
        Store.storeToFile(file, new CLFGenerator(System.currentTimeMillis(), window, delta), false);
        try {
            runControl1.awaitAlarmToGoOff();
            runControl3.awaitAlarmToGoOff();
            runControl3.awaitReadComplete();
            runControl1.awaitReadComplete();
        } finally {
            watcher.stop();
            Files.deleteIfExists(file);
            assertThat(runControl1.getBytesRead(), greaterThan(alarmBytesThreshold));
            assertThat(runControl3.getBytesRead(), greaterThan(alarmBytesThreshold));
            assertThat(runControl1.getLinesRead(), is(window / delta));
            assertThat(runControl3.getLinesRead(), is(window / delta));
        }
    }

    private static void contextSwitch(long timeout) {
        try {
            TimeUnit.MILLISECONDS.sleep(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("should not happen");
        }
    }
}
