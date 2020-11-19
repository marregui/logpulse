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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class Store {

    private static final Path STORE_FOLDER = Paths.get("logpulse-store").toAbsolutePath();

    static {
        try {
            Files.createDirectories(STORE_FOLDER);
        } catch (IOException e) {
            throw new Error("could not create: " + STORE_FOLDER);
        }
    }

    public static String accessLogFileName() {
        return "access_" + UUID.randomUUID() + ".log";
    }

    public static String nestedAccessLogFileName() {
        return UUID.randomUUID() + File.separator + "access.log";
    }

    public static Path resolve(String fileName) {
        return STORE_FOLDER.resolve(fileName);
    }

    public static String[] loadFileContents(Path file) {
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<String> entries = new ArrayList<>();
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                entries.add(line.strip());
            }
            return entries.toArray(new String[entries.size()]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String[] loadResourceContents(String resourceName) {
        URL resource = CLFGenerator.class.getClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new IllegalArgumentException(String.format(
                    "resource [%s] not found",
                    resourceName));
        }
        try {
            return loadFileContents(Paths.get(resource.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static <T extends WithUTCTimestamp> long storeToFile(Path file, Iterable<T> lines, boolean append) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rws");
             FileChannel channel = raf.getChannel()) {
            if (append) {
                raf.seek(channel.size());
            }
            long count = 0;
            long startTs = 0;
            long endTs = 0;
            for (WithUTCTimestamp line : lines) {
                endTs = line.getUTCTimestamp();
                if (startTs == 0) {
                    startTs = endTs;
                }
                channel.write(wrap(line.toString()));
                count++;
            }
            System.out.printf("Stored %d lines, from: %s, to: %s%n",
                    Long.valueOf(count),
                    UTCTimestamp.formatForDisplay(startTs),
                    UTCTimestamp.formatForDisplay(endTs));
            return count;
        }
    }

    public static <T extends WithUTCTimestamp> CompletableFuture<Void> storeToFileAsync(Path file,
                                                                                        Iterable<T> lines,
                                                                                        boolean append,
                                                                                        long delayMillis) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (delayMillis > 0L) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("was interrupted", e);
                    }
                }
                storeToFile(file, lines, append);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static ByteBuffer wrap(String line) {
        return ByteBuffer.wrap((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
    }
}
