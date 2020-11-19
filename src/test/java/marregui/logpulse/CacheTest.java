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
import marregui.logpulse.clf.CLFParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CacheTest {

    private List<CLF> logLines;

    @BeforeEach
    public void beforeEach() {
        logLines = Stream.of(
                "127.0.0.1 - admin [10/11/2020:16:00:00 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:01 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:02 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:03 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020",
                "127.0.0.1 - admin [10/11/2020:16:00:04 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020")
                .map(CLFParser::parseLogLine)
                .collect(Collectors.toList());
    }

    @Test
    public void test_cache_firstTimestamp_with_timestamp() throws ParseException {
        long ts = UTCTimestamp.parse("10/11/2020:16:00:03 +0000");
        ReadoutCache<CLF> cache = new ReadoutCache<>();
        cache.addAll(logLines);
        long next = cache.firstTimestampSince(ts);
        assertThat(UTCTimestamp.format(next), is("10/11/2020:16:00:04 +0000"));
        ts = UTCTimestamp.parse("10/11/2020:16:00:04 +0000");
        assertThat(cache.firstTimestampSince(ts), is(-1L));
    }

    @Test
    public void test_cache_fetch_followed_by_evict() {
        long ts0 = logLines.get(0).getUTCTimestamp();
        ReadoutCache<CLF> cache = new ReadoutCache<>();
        cache.addAll(logLines);
        List<CLF> cacheLine = cache.fetch(ts0, ts0);
        cache.evict(1);
        assertThat(cacheLine.size(), is(1));
        assertThat(cacheLine.get(0).toString(),
                is("127.0.0.1 - admin [10/11/2020:16:00:00 +0000] \"GET /resources/index.php HTTP/2.0\" 200 2020"));
        logLines.stream()
                .map(clf -> Long.valueOf(UTCTimestamp.truncateMillis(clf.getUTCTimestamp())))
                .distinct()
                .skip(1)
                .forEach(ts -> {
                    List<CLF> lines = cache.fetch(ts, ts);
                    assertThat(lines.size(), is(10));
                    int cacheSize = cache.size();
                    cache.evict(lines.size());
                    assertThat(cache.size(), is(cacheSize - lines.size()));
                });
        assertThat(cache.isEmpty(), is(true));
    }

    @Test
    public void test_slideBack() {
        int size = logLines.size();
        long ts = logLines.get(size >>> 1).getUTCTimestamp();
        int idx = ReadoutCache.findNearest(logLines, ts);
        assertThat(idx, is(size >>> 1));
        idx = ReadoutCache.slideBack(logLines, idx);
        int i = 0;
        while (i < size && ts(logLines, i) != ts) {
            i++;
        }
        assertThat(idx, is(i));
    }

    @Test
    public void test_slideForward() {
        int size = logLines.size();
        long ts = logLines.get(size >>> 1).getUTCTimestamp();
        int idx = ReadoutCache.findNearest(logLines, ts);
        assertThat(idx, is(size >>> 1));
        idx = ReadoutCache.slideForward(logLines, idx);
        int i = size - 1;
        while (i >= 0 && ts(logLines, i) != ts) {
            i--;
        }
        assertThat(idx, is(i));
    }

    @Test
    public void test_findNearest() {
        List<WithUTCTimestamp> timestamps = LongStream.iterate(System.currentTimeMillis(), l -> l + 2L)
                .mapToObj(l -> (WithUTCTimestamp) () -> l)
                .limit(4)
                .collect(Collectors.toList());
        for (int i = 0; i < timestamps.size(); i++) {
            long tsAtI = ts(timestamps, i);
            int idx = ReadoutCache.findNearest(timestamps, tsAtI + 1L);
            assertThat(idx, is(i));
        }
        for (int i = timestamps.size() - 1; i > 0; i--) {
            long tsAtI = ts(timestamps, i);
            int idx = ReadoutCache.findNearest(timestamps, tsAtI - 1L);
            assertThat(idx, is(i - 1));
        }
    }

    public static <T extends WithUTCTimestamp> long ts(List<T> entries, int idx) {
        return entries.get(idx).getUTCTimestamp();
    }

}
