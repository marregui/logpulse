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

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.fail;

public class TaskProcessorTest {

    @Test
    public void test_processor_cannot_be_started_twice() {
        TaskProcessor processor = new TaskProcessor(1, false);
        try {
            processor.start();
            processor.start();
            fail("should not happen");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("already running"));
        } finally {
            processor.stop();
        }
    }

    @Test
    public void test_processor_cannot_be_stopped_if_not_running() {
        TaskProcessor processor = new TaskProcessor(1, false);
        try {
            processor.stop();
            fail("should not happen");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("not running"));
        }
    }

    @Test
    public void test_processor_start_stop_is_running() {
        TaskProcessor processor = new TaskProcessor(1, false);
        assertThat(processor.isRunning(), is(false));
        processor.start();
        processor.joinTasks(0L);
        assertThat(processor.isRunning(), is(true));
        assertThat(processor.getRunningTasksCount(), is(0));
        processor.stop();
        processor.joinTasks(0L);
        assertThat(processor.isRunning(), is(false));
    }

    @Test
    public void test_processor_can_be_joined() {
        TaskProcessor processor = new TaskProcessor(1, false);
        processor.start();
        long start = System.currentTimeMillis();
        processor.processTask(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(80L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        while (processor.joinTasks(100L)) {
            processor.stop();
        }
        assertThat(System.currentTimeMillis() - start, lessThan(100L));
    }
}
