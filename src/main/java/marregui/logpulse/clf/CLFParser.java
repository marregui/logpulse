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

import marregui.logpulse.UTCTimestamp;

import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;
import static marregui.logpulse.clf.CLF.HTTPMethod;

/**
 * Utilities type class, contains the logic for parsing UTF-8 encoded
 * Common Logging Format (CLF) formatted log lines.
 * <p>
 * A line is composed of:
 * <p>
 * <b>host ident authuser date request status bytes</b>
 * <p>
 * where:
 * <ul>
 *     <li><b>host</b>: IP address, or host name, of the client (remote host)
 *     that made the request to the server.</li>
 *
 *     <li><b>ident</b>: RFC 1413 identity of the client. Usually "-".</li>
 *
 *     <li><b>authuser</b>: userid of the user requesting the resource. Usually
 *     "-" unless .htaccess has requested authentication.</li>
 *
 *     <li><b>date</b>: date, time and time zone when the request was received,
 *     the format is <i>dd/MMMM/yyyy:HH:mm:ss Z</i>.</li>
 *
 *     <li><b>request</b>: request line from the client.</li>
 *
 *     <li><b>status</b>: HTTP status code returned to the client (2xx successful,
 *     3xx redirection, 4xx client error, 5xx server error).</li>
 *
 *     <li><b>bytes</b>: size of the object returned to the client, measured in bytes.</li>
 * </ul>
 * <p>
 * A "-" in a field means missing data.
 * <p>
 * Example log lines:
 * <pre>
 * 127.0.0.1 - james [09/May/2018:16:00:39 +0000] "GET /report HTTP/1.0" 200 123
 * 127.0.0.1 - jill [09/May/2018:16:00:41 +0000] "GET /api/user HTTP/1.0" 200 234
 * 127.0.0.1 - frank [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 200 34
 * 127.0.0.1 - mary [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 503 12
 * </pre>
 *
 * @see <a href="https://publib.boulder.ibm.com/tividd/td/ITWSA/ITWSA_info45/en_US/HTML/guide/c-logs.html#common">More on CLF</a>
 */
public final class CLFParser {

    /**
     * Parses a log line in CLF format.
     * @param logLine UTF-8 encoded log line
     * @return an instance of CLF
     * @throws IllegalArgumentException when the logLine's format is incorrect
     * @see CLF
     */
    public static CLF parseLogLine(CharSequence logLine) {
        Objects.requireNonNull(logLine);
        Map<State, String> tokens = runStateMachine(logLine);
        CLF.Builder builder = CLF.builder();
        builder.host(tokens.get(State.IP));
        builder.ident(tokens.get(State.IDENT));
        builder.authUser(tokens.get(State.AUTHUSER));
        try {
            builder.timestamp(UTCTimestamp.parse(tokens.get(State.DATETIME)));
        } catch (ParseException e) {
            throw incorrectFormat(tokens, State.DATETIME, e);
        }
        Matcher m = REQUEST_PATTERN.matcher(tokens.get(State.REQUEST));
        if (m.find()) {
            builder.method(CLF.HTTPMethod.valueOf(m.group(1)));
            builder.resource(m.group(2));
            builder.version(m.group(3));
        } else {
            throw incorrectRequestFormat(tokens.get(State.REQUEST));
        }
        try {
            builder.status(Integer.parseInt(tokens.get(State.STATUS)));
        } catch (NumberFormatException e) {
            throw incorrectFormat(tokens, State.STATUS, e);
        }
        try {
            builder.bytes(Long.parseLong(tokens.get(State.BYTES)));
        } catch (NumberFormatException e) {
            throw incorrectFormat(tokens, State.BYTES, e);
        }
        return builder.build();
    }

    static final Pattern REQUEST_PATTERN = Pattern.compile(String.format(
            "^(%s) (\\S+) HTTP/(\\d.\\d)$", // three groups, of indexes 1, 2, 3
            Arrays.stream(HTTPMethod.values()).map(HTTPMethod::name).collect(joining("|"))));

    private static IllegalArgumentException incorrectFormat(Map<State, String> tokens, State st, Exception e) {
        return new IllegalArgumentException(String.format(
                "incorrect %s format: %s", st.name().toLowerCase(), tokens.get(st)), e);
    }

    private static IllegalArgumentException incorrectRequestFormat(String request) {
        return new IllegalArgumentException(String.format(
                "incorrect %s format: %s", State.REQUEST.name().toLowerCase(), request));
    }

    private static Map<State, String> runStateMachine(CharSequence logLine) {
        Map<State, String> tokens = new HashMap<>();
        State st = State.IP;
        int start = 0;
        for (int offset = start; offset < logLine.length(); offset++) {
            offset = st.consumeToken(logLine, offset);
            if (st.hasDelimiter()) {
                tokens.put(st, logLine.subSequence(start + 1, offset - 1).toString());
            } else {
                tokens.put(st, logLine.subSequence(start, offset).toString());
            }
            start = offset + 1;
            st = st.next();
        }
        if (st != State.END) {
            throw new IllegalArgumentException(String.format(
                    "incorrect format, last state [%s], logLine: %s", st, logLine));
        }
        return tokens;
    }

    private enum State implements Iterator<State> {
        END(),
        BYTES(END),
        STATUS(BYTES),
        REQUEST(STATUS, '"'),
        DATETIME(REQUEST, '[', ']'),
        AUTHUSER(DATETIME),
        IDENT(AUTHUSER),
        IP(IDENT);

        private static final char NULL_DELIM = '\0';
        private static final char SPACE_DELIM = ' ';

        private final State next;
        private final char openDelimiter;
        private final char closeDelimiter;

        State(State next, char openDelimiter, char closeDelimiter) {
            this.next = next;
            this.openDelimiter = openDelimiter;
            this.closeDelimiter = closeDelimiter;
        }

        State(State next, char delimiter) {
            this(next, delimiter, delimiter);
        }

        State(State next) {
            this(next, NULL_DELIM, SPACE_DELIM);
        }

        State() {
            this(null, NULL_DELIM, NULL_DELIM); // End state
        }

        boolean hasDelimiter() {
            return openDelimiter != NULL_DELIM;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public State next() {
            if (!hasNext()) {
                throw new IllegalStateException("end state does not have next");
            }
            return next;
        }

        private int consumeToken(CharSequence logLine, int offset) {
            int i = offset;
            if (openDelimiter != NULL_DELIM) {
                if (openDelimiter != logLine.charAt(offset)) {
                    throw new IllegalArgumentException(String.format(
                            "parsing [%s] offset:%d, expected:%c, found:%c, logLine: %s",
                            this,
                            offset,
                            openDelimiter,
                            logLine.charAt(offset),
                            logLine));
                }
                i++;
            }
            while (i < logLine.length() && logLine.charAt(i) != closeDelimiter) {
                i++;
            }
            if (i == logLine.length()) {
                return i;
            }
            if (closeDelimiter != NULL_DELIM) {
                if (closeDelimiter != logLine.charAt(i)) {
                    throw new IllegalArgumentException(String.format(
                            "parsing [%s] offset:%d, expected:%c, found:%c, logLine:%s",
                            this,
                            i,
                            closeDelimiter,
                            logLine.charAt(i),
                            logLine));
                }
            }
            return closeDelimiter == SPACE_DELIM ? i : i + 1;
        }
    }
}
