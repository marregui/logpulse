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
package marregui.logpulse.clf.stats;

import marregui.logpulse.clf.CLF;
import marregui.logpulse.clf.CLFParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class GeneralStatsTest {

    private List<CLF> logLines;

    @BeforeEach
    public void beforeEach() {
        logLines = Stream.of(
                "192.168.0.17 - lina [05/November/2020:16:09:42 +0000] \"GET /wp-content/plugins/woocommerce/vendor/maxmind-db/reader HTTP/1.1\" 300 3621",
                "192.168.0.17 - lina [05/November/2020:16:09:43 +0000] \"OPTIONS /wp-content/plugins/jetpack/_inc/blocks/opentable HTTP/2.0\" 300 3811",
                "chihuahua.logpulse.com - miguel [05/November/2020:16:09:44 +0000] \"OPTIONS /wp-content/plugins/jetpack/modules/infinite-scroll/themes/twentysixteen-rtl.css HTTP/2.0\" 200 3131",
                "chihuahua.logpulse.com - admin [05/November/2020:16:09:45 +0000] \"PUT /wp-content/plugins/jetpack/_inc/jetpack-deactivate-dialog.js HTTP/1.1\" 300 273",
                "127.0.0.1 - admin [05/November/2020:16:09:46 +0000] \"HEAD /wp-content/plugins/woocommerce-services/images HTTP/2.0\" 500 1924",
                "chihuahua.logpulse.com - miguel [05/November/2020:16:09:47 +0000] \"GET /wp-content/plugins/jetpack/modules/infinite-scroll/themes/twentyten.css HTTP/1.1\" 200 4036",
                "chihuahua.logpulse.com - lina [05/November/2020:16:09:48 +0000] \"PUT /wp-content/plugins/woocommerce/packages/woocommerce-admin/vendor/composer/installers/src/Composer/Installers/AimeosInstaller.php HTTP/1.0\" 300 848",
                "chihuahua.logpulse.com - lina [05/November/2020:16:09:49 +0000] \"GET /wp-content/plugins/woocommerce/assets/css/jquery-ui/jquery-ui-rtl.css HTTP/2.0\" 500 983",
                "192.168.0.17 - admin [05/November/2020:16:09:50 +0000] \"PUT /wp-content/plugins/jetpack/css/cleanslate-rtl.css HTTP/1.1\" 300 3404",
                "127.0.0.1 - lina [05/November/2020:16:09:51 +0000] \"PUT /wp-content/plugins/jetpack/modules/shortcodes/css HTTP/1.0\" 400 745")
                .map(CLFParser::parseLogLine)
                .collect(Collectors.toList());
    }

    @Test
    public void test_trigger_alarm() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            GeneralStatsView gs = new GeneralStatsView(ps);
            long periodStart = logLines.get(0).getUTCTimestamp();
            long periodEnd = logLines.get(logLines.size() - 1).getUTCTimestamp();
            gs.executeSchedule(periodStart, periodEnd, logLines);
        }
        assertThat(baos.toString(StandardCharsets.UTF_8),
                is("""
                        General HTTP Traffic Statistics
                        ===============================
                        Period: 10 seconds
                        From: 05/11/2020:16:09:42 +0000 (1604592582000)
                        To: 05/11/2020:16:09:51 +0000 (1604592591000)
                        Count: 10
                        Logs per second: 1.00
                        Count per section:
                         - /wp-content: 10
                        Count per method:
                         - PUT: 4
                         - GET: 3
                         - OPTIONS: 2
                         - HEAD: 1
                        Count per version:
                         - 2.0: 4
                         - 1.1: 4
                         - 1.0: 2
                        Count per status category:
                         - Redirection: 5
                         - Success: 2
                         - ServerError: 2
                         - ClientError: 1
                        Total received (POST, PUT): 5.15KB (527.00Bps)
                        Total sent (GET, HEAD, PATCH, OPTIONS, DELETE): 17.10KB (1.71KBps)
                        Total IO: 22.24KB (2.22KBps)
                        """
        ));
    }
}
