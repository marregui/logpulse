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

import java.text.ParseException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UTCTimestampTest {

    @Test
    public void test_parse() throws ParseException {
        assertThat(
                UTCTimestamp.parse("10/November/2020:16:00:00 +0000"),
                is(UTCTimestamp.parse("10/11/2020:16:00:00 +0000")));
    }

    @Test
    public void test_format() {
        assertThat(
                UTCTimestamp.format(1604311200000L),
                is("02/11/2020:10:00:00 +0000"));
    }

    @Test
    public void test_formatForDisplay() {
        long ts = 1604311200000L;
        assertThat(
                UTCTimestamp.formatForDisplay(ts),
                is("02/11/2020:10:00:00 +0000 (" + ts + ")"));
    }
}
