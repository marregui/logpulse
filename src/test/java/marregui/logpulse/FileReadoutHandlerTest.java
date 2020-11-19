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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FileReadoutHandlerTest {

    @Test
    public void test_concurrent_writer_and_reader_agree_on_file_contents_and_interpretation() throws InterruptedException, IOException {
        Path file = Store.resolve(Store.accessLogFileName());
        AtomicLong totalLogLinesRead = new AtomicLong();
        AtomicLong totalLogLinesWritten = new AtomicLong();
        CountDownLatch dataAvailable = new CountDownLatch(1);
        long maxLogs = 15_000L;
        long width = 25 * 1000L;
        long delta = 1L;

        Thread logLinesProducer = new Thread(() -> {
            long start = System.currentTimeMillis();
            for (long i = 0; !Thread.currentThread().isInterrupted(); i++) {
                try {
                    long count = Store.storeToFile(file, new CLFGenerator(start, width / 5, delta), i > 0);
                    if (dataAvailable.getCount() > 0) {
                        dataAvailable.countDown();
                    }
                    if (totalLogLinesWritten.addAndGet(count) >= maxLogs) {
                        break;
                    }
                    start = start + width;
                } catch (IOException e) {
                    break;
                }
            }
        }, "logLinesProducer");

        ReadoutCache<CLF> loadedLogLines = new ReadoutCache<>();
        Thread logLinesConsumer = new Thread(() -> {
            FileReadoutHandler<CLF> readoutHandler = new CLFReadoutHandler(file);
            int consecutiveNothingReadCount = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    List<CLF> lines = readoutHandler.fetchAvailableLines();
                    if (!lines.isEmpty()) {
                        loadedLogLines.addAll(lines);
                        totalLogLinesRead.addAndGet(lines.size());
                        consecutiveNothingReadCount = 0;
                    } else {
                        consecutiveNothingReadCount++;
                        try {
                            TimeUnit.MILLISECONDS.sleep(100L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (consecutiveNothingReadCount > 10) {
                            break;
                        }
                    }
                } catch (IllegalStateException e) {
                    // does not exist yet
                } catch (IOException e) {
                    break;
                }
            }
        }, "logLinesConsumer");

        try {
            long startTs = System.currentTimeMillis();
            logLinesProducer.start();
            logLinesConsumer.start();
            logLinesProducer.join();
            logLinesConsumer.join();
            long elapsedTs = System.currentTimeMillis() - startTs;
            System.out.println("Total lines written: " + totalLogLinesWritten);
            System.out.println("Total lines read: " + totalLogLinesRead);
            System.out.println("Total millis: " + elapsedTs);
            assertThat(totalLogLinesWritten.get(), is(totalLogLinesRead.get()));
            assertThat(totalLogLinesRead.get(), is(maxLogs));
            int fileSize = new CLFReadoutHandler(file).fetchAvailableLines().size();
            assertThat(fileSize, is(loadedLogLines.size()));
            assertThat(fileSize, is((int) maxLogs));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
