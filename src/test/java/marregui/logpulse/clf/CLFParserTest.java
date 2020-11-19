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
package marregui.logpulse.clf;

import marregui.logpulse.CLFGenerator;
import marregui.logpulse.Store;
import marregui.logpulse.UTCTimestamp;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

public class CLFParserTest {

    @Test
    public void test_correct_resource_patterns() {
        testCorrectRequestPattern("GET / HTTP/1.0");
        testCorrectRequestPattern("HEAD /scripts HTTP/1.0");
        testCorrectRequestPattern("POST /scripts/start HTTP/1.0");
        testCorrectRequestPattern("PUT /images/banana.gif HTTP/1.0");
        testCorrectRequestPattern("DELETE /images/holidays/palm_tree.gif HTTP/1.0");
        testCorrectRequestPattern("OPTIONS /config HTTP/1.0");
    }

    @Test
    public void test_incorrect_resource_patterns() {
        testIncorrectRequestPattern("/index.html HTTP/1.0");
        testIncorrectRequestPattern("GET HTTP/1.0");
        testIncorrectRequestPattern("GET //");
        testIncorrectRequestPattern("SAUSAGE /index.html HTTP/1.0");
        testIncorrectRequestPattern("GET /index.html HTTP/1.0 badum-tss");
    }

    @Test
    public void test_parse_correct_line() {
        String logLine = "127.0.0.1 - miguel [02/Nov/2020:10:00:00 +0100] \"HEAD /joined-logpulse HTTP/1.0\" 200 999";

        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(UTCTimestamp.UTC);
        cal.set(Calendar.YEAR, 2020);
        cal.set(Calendar.MONTH, Calendar.NOVEMBER);
        cal.set(Calendar.DATE, 2);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long timestamp = cal.getTime().getTime();

        CLF clf = CLFParser.parseLogLine(logLine);
        assertThat(clf.getHost(), is("127.0.0.1"));
        assertThat(clf.getIdent(), is("-"));
        assertThat(clf.getAuthUser(), is("miguel"));
        assertThat(clf.getUTCTimestamp(), is(timestamp));
        assertThat(clf.getMethod(), is(CLF.HTTPMethod.HEAD));
        assertThat(clf.getResource(), is("/joined-logpulse"));
        assertThat(clf.getVersion(), is("1.0"));
        assertThat(clf.getStatus(), is(200));
        assertThat(clf.getBytes(), is(999L));
    }

    @Test
    public void test_parse_incorrect_line_missing_bytes_field() {
        String logLine = "127.0.0.1 - miguel [02/Nov/2020:10:00:00 +0100] \"HEAD /joined-logpulse HTTP/1.0\" 200";
        parseLogLineExpectingFailure(logLine, "incorrect format, last state [BYTES], logLine: " + logLine);
    }

    @Test
    public void test_parse_incorrect_line_missing_ident_field() {
        String logLine = "127.0.0.1 miguel [02/Nov/2020:10:00:00 +0100] \"HEAD /joined-logpulse HTTP/1.0\" 200 999";
        parseLogLineExpectingFailure(
                logLine,
                "parsing [DATETIME] offset:39, expected:[, found:+, logLine: " + logLine);
    }

    @Test
    public void test_parse_incorrect_datetime_missing_fields() {
        parseLogLineExpectingFailure(
                "127.0.0.1 - miguel [02/Nov/2020:10 +0100] \"HEAD /joined-logpulse HTTP/1.0\" 200 999",
                "incorrect datetime format: 02/Nov/2020:10 +0100");
    }

    @Test
    public void test_parse_incorrect_request_missing_version_field() {
        parseLogLineExpectingFailure(
                "127.0.0.1 - miguel [02/Nov/2020:10:00:00 +0100] \"HEAD /joined-logpulse HTTP\" 200 999",
                "incorrect request format: HEAD /joined-logpulse HTTP");
    }

    @Test
    public void test_parse_format_date_time_are_symmetric() throws ParseException {
        String dateTime = "02/Nov/2020:10:00:00 +0100";
        long timestamp = UTCTimestamp.parse(dateTime);
        assertThat(timestamp, is(1604307600000L));
        assertThat(UTCTimestamp.format(timestamp), is("02/11/2020:09:00:00 +0000"));
    }

    @Test
    public void test_bulk_parse() throws IOException {
        Path file = Store.resolve(Store.accessLogFileName());
        Store.storeToFile(file, new CLFGenerator(System.currentTimeMillis(), 5 * 60_000L, 1000L), false);
        try {
            List<String> logLines = Arrays.asList(Store.loadFileContents(file));
            List<String> reconstructedLogLines = logLines
                    .stream()
                    .map(CLFParser::parseLogLine)
                    .map(CLF::toString)
                    .collect(Collectors.toList());
            assertThat(logLines, is(reconstructedLogLines));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private void testCorrectRequestPattern(String request) {
        Matcher m = CLFParser.REQUEST_PATTERN.matcher(request);
        assertThat(m.find(), is(true));
        assertThat(m.group(0), is(request));
        String[] parts = request.split(" ");
        assertThat(parts.length, is(3));
        assertThat(m.group(1), is(parts[0]));
        assertThat(m.group(2), is(parts[1]));
        assertThat(m.group(3), is(parts[2].substring(parts[2].indexOf("/") + 1)));
    }

    private void testIncorrectRequestPattern(String request) {
        Matcher m = CLFParser.REQUEST_PATTERN.matcher(request);
        assertThat(m.matches(), is(false));
        assertThat(m.find(), is(false));
    }

    private static void parseLogLineExpectingFailure(String logLine, String message) {
        try {
            CLFParser.parseLogLine(logLine);
            fail("expected a failure: " + message);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(message));
        }
    }
}
