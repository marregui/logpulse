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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utilities type class containing methods to handle timestamps, which
 * internally are converted to a UTC Epoch (a long).
 * <p>
 * The date format is assumed to be:
 * <a href="https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/text/SimpleDateFormat.html">dd/MMMM/yyyy:HH:mm:ss Z</a>
 * <p>
 * <b>NOTE</b>: In the future, this class should be abstract, and subclasses should provide
 * file specific date formatting.<br/>
 * <b>NOTE</b>: SimpleDateFormat is not thread safe, so we use two instances (parse and
 * format are called by different threads).
 */
public final class UTCTimestamp {

    /**
     * UTC
     */
    public static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final SimpleDateFormat [] DATETIME_IN = {
            new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss Z", Locale.ENGLISH),
            new SimpleDateFormat("dd/MMMM/yyyy:HH:mm:ss Z", Locale.ENGLISH)
    };
    private static final SimpleDateFormat DATETIME_OUT =
            new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss Z", Locale.ENGLISH);
    static {
        DATETIME_OUT.setTimeZone(UTC);
        Arrays.stream(DATETIME_IN).forEach(dt -> dt.setTimeZone(UTC));
    }

    /**
     *
     * @param dateTimeZ in the format "dd/MMMM/yyyy:HH:mm:ss Z"
     * @return the UTC Epoch representing the dateTime
     * @throws ParseException when the format is not followed
     */
    public static long parse(String dateTimeZ) throws ParseException {
        for (int i = 0; i < DATETIME_IN.length; i++) {
            try {
                return DATETIME_IN[i].parse(dateTimeZ).getTime();
            } catch (ParseException ignore) {
                // try next
            }
        }
        throw new ParseException(dateTimeZ, 0);
    }

    /**
     * @param ts a UTC Epoch
     * @return formatted with "dd/MMMM/yyyy:HH:mm:ss Z"
     */
    public static String format(long ts) {
        return DATETIME_OUT.format(Long.valueOf(ts));
    }

    /**
     * @param ts a UTC Epoch
     * @return a display rendering of the timestamp '{text} ({UTC Epoch millis})'
     */
    public static String formatForDisplay(long ts) {
        return UTCTimestamp.format(ts) + " (" + ts + ")";
    }

    /**
     * @param ts a UTC Epoch
     * @return the original Epoch dropping Millis precision
     */
    public static long truncateMillis(long ts) {
        return (ts / 1000L) * 1000L;
    }
}
