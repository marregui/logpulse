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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class CLFGenerator implements Iterable<CLF>, Iterator<CLF> {

    private static final String[] HOSTS = {"127.0.0.1", "chihuahua.some-domain.com", "192.168.0.17"};
    private static final String[] AUTH_USERS = {"miguel", "lina", "admin"};
    private static final String[] RESOURCES = Store.loadResourceContents("generator/webroot_structure.txt");
    private static final String[] VERSIONS = {"1.0", "1.1", "2.0"};
    private static final Integer[] STATUS = {
            200, // success
            300, // redirection
            400, // client error
            500, // server error
    };
    private static final int MIN_BYTES = 64;
    private static final int MAX_BYTES = 1024 * 4;

    private final long end;
    private final long delta;
    private final AtomicLong logCount;
    private long ts;
    private final ThreadLocalRandom rand;
    private final CLF.Builder builder;

    public CLFGenerator(long start, long windowMillis, long delta) {
        end = start + windowMillis;
        if (start < 0 || end < 0 || start >= end) {
            throw new IllegalArgumentException(String.format(
                    "invalid range [%d, %d] (window: %d)", start, end, windowMillis));
        }
        if (delta < 0) {
            throw new IllegalArgumentException(String.format("invalid delta %d, must be > 0", delta));
        }
        this.delta = delta;
        ts = start;
        this.logCount = new AtomicLong();
        rand = ThreadLocalRandom.current();
        builder = CLF.builder();
    }

    @Override
    public boolean hasNext() {
        return ts < end;
    }

    @Override
    public CLF next() {
        if (!hasNext()) {
            throw new NoSuchElementException("exhausted");
        }
        builder.host(randOf(HOSTS, String.class));
        builder.ident("-");
        builder.authUser(randOf(AUTH_USERS, String.class));
        builder.timestamp(ts);
        builder.method(randOf(CLF.HTTPMethod.values(), CLF.HTTPMethod.class));
        builder.resource(randOf(RESOURCES, String.class));
        builder.version(randOf(VERSIONS, String.class));
        builder.status(randOf(STATUS, Integer.class));
        builder.bytes(rand.nextInt(MIN_BYTES, MAX_BYTES + 1));
        CLF log = builder.build();
        builder.reset();
        ts += delta;
        logCount.incrementAndGet();
        return log;
    }

    @Override
    public Iterator<CLF> iterator() {
        return this;
    }

    private <T> T randOf(Object[] array, Class<T> clazz) {
        return clazz.cast(array[rand.nextInt(0, array.length)]);
    }

    public static void main(String... args) throws Exception {
        // circa 150 bytes per log line -> 8.5MB
        long start = System.currentTimeMillis();
        long window = 1000L;
        long delta = 10L;
        String fileName = "testLogs.log";
        Path file = Store.resolve(fileName);
        Files.deleteIfExists(file);
        Store.storeToFileAsync(
                file,
                new CLFGenerator(start, window, delta),
                false,
                0L)
                .thenRun(() -> System.out.printf(
                        "CLF logs from %s to %s with delta %d: %s",
                        UTCTimestamp.format(start),
                        UTCTimestamp.format(start + window),
                        delta,
                        file)).get();
    }
}
