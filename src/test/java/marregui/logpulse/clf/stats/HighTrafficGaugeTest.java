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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HighTrafficGaugeTest {

    private List<CLF> logLines;

    @BeforeEach
    public void beforeEach() {


        logLines = Stream.of(
                // 5 rps
                // avg: 5.00, sum: 5
                "127.0.0.1 - lina [13/11/2020:12:30:21 +0000] \"DELETE /wp-content/plugins/jetpack/modules/shortcodes/css HTTP/1.0\" 200 2185",
                "192.168.0.17 - lina [13/11/2020:12:30:21 +0000] \"OPTIONS /wp-content/plugins/woocommerce/vendor/maxmind-db/reader HTTP/1.1\" 300 1920",
                "192.168.0.17 - lina [13/11/2020:12:30:21 +0000] \"PATCH /wp-content/plugins/jetpack/modules/infinite-scroll/themes/twentyten.css HTTP/1.1\" 500 3464",
                "127.0.0.1 - miguel [13/11/2020:12:30:21 +0000] \"GET /wp-content/plugins/woocommerce/packages/action-scheduler/classes/ActionScheduler_WPCommentCleaner.php HTTP/2.0\" 400 1977",
                "chihuahua.logpulse.com - lina [13/11/2020:12:30:21 +0000] \"PATCH /wp-content/plugins/woocommerce/templates/single-product/price.php HTTP/1.1\" 200 2377",

                // 10 rps
                // avg: 7.50, sum: 15
                "192.168.0.17 - admin [13/11/2020:12:30:22 +0000] \"PUT /wp-content/plugins/woocommerce/assets HTTP/1.1\" 300 1663",
                "192.168.0.17 - admin [13/11/2020:12:30:22 +0000] \"PUT /wp-content/plugins/jetpack/modules/infinite-scroll/themes/twentyten.css HTTP/1.1\" 400 3784",
                "192.168.0.17 - lina [13/11/2020:12:30:22 +0000] \"DELETE /wp-content/plugins/jetpack/_inc/jetpack-deactivate-dialog.js HTTP/2.0\" 500 1161",
                "chihuahua.logpulse.com - admin [13/11/2020:12:30:22 +0000] \"POST /wp-includes/css/customize-preview-rtl.css HTTP/2.0\" 500 2624",
                "chihuahua.logpulse.com - admin [13/11/2020:12:30:22 +0000] \"HEAD /wp-content/plugins/woocommerce/templates/single-product/price.php HTTP/2.0\" 200 436",
                "chihuahua.logpulse.com - miguel [13/11/2020:12:30:22 +0000] \"GET /wp-content/plugins/woocommerce/vendor/maxmind-db/reader HTTP/1.1\" 300 2075",
                "192.168.0.17 - admin [13/11/2020:12:30:22 +0000] \"PATCH /wp-includes/sodium_compat/src/Core/Base64/Original.php HTTP/1.1\" 400 3462",

                //  High Traffic Gauge (7.40 req. per sec.): High Traffic - hits = {12}, avg: 7.50, triggered: {13/11/2020:12:30:22 +0000 (1605270622000)}
                "127.0.0.1 - admin [13/11/2020:12:30:22 +0000] \"DELETE /wp-content/plugins/jetpack/modules/custom-css/custom-css/preprocessors/lessc.inc.php HTTP/2.0\" 500 587",

                "chihuahua.logpulse.com - admin [13/11/2020:12:30:22 +0000] \"POST /wp-content/plugins/woocommerce-services/images HTTP/2.0\" 500 1141",
                "127.0.0.1 - admin [13/11/2020:12:30:22 +0000] \"PATCH /wp-content/plugins/jetpack/modules/widgets/top-posts/style.css HTTP/1.0\" 500 1757",

                // 6 rps
                // avg: 7.00, sum: 21
                // High Traffic Gauge (7.40 req. per sec.): High Traffic - hits = {15}, avg: 7.50, triggered: {13/11/2020:12:30:23 +0000 (1605270623000)}
                "127.0.0.1 - miguel [13/11/2020:12:30:23 +0000] \"PATCH /wp-content/plugins/woocommerce/includes/rest-api/Controllers/Version3/class-wc-rest-coupons-controller.php HTTP/1.0\" 500 4008",
                "192.168.0.17 - admin [13/11/2020:12:30:23 +0000] \"PATCH /wp-includes/css/customize-preview-rtl.css HTTP/2.0\" 500 487",
                "chihuahua.logpulse.com - lina [13/11/2020:12:30:23 +0000] \"GET /wp-content/plugins/woocommerce/packages/woocommerce-blocks/assets/js/data/cart/test/selectors.js HTTP/1.0\" 200 1741",
                "127.0.0.1 - miguel [13/11/2020:12:30:23 +0000] \"DELETE /wp-includes/js/tinymce/utils/editable_selects.js HTTP/1.0\" 300 893",
                "192.168.0.17 - miguel [13/11/2020:12:30:23 +0000] \"HEAD /wp-content/plugins/woocommerce/assets HTTP/2.0\" 500 2054",
                "127.0.0.1 - admin [13/11/2020:12:30:23 +0000] \"PUT /wp-content/plugins/jetpack/modules/search.php HTTP/1.0\" 300 2226",

                // 2 rps
                // avg: 5.75, sum: 23
                // High Traffic Gauge (7.40 req. per sec.): Traffic is back to normal - hits = {21}, avg: 7.00, triggered: {13/11/2020:12:30:24 +0000 (1605270624000)}
                "chihuahua.logpulse.com - admin [13/11/2020:12:30:24 +0000] \"HEAD /wp-content/plugins/woocommerce-services/images HTTP/1.1\" 200 1247",
                "127.0.0.1 - miguel [13/11/2020:12:30:24 +0000] \"PATCH /wp-content/plugins/jetpack/modules/widgets/top-posts/style.css HTTP/2.0\" 300 3980",

                // 27 rps
                // avg: 10.00, sum: 50
                "192.168.0.17 - admin [13/11/2020:12:30:25 +0000] \"DELETE /wp-includes/Requests/Exception/HTTP/305.php HTTP/1.1\" 300 2773",
                "192.168.0.17 - miguel [13/11/2020:12:30:25 +0000] \"OPTIONS /wp-content/plugins/woocommerce/assets/css/jquery-ui/jquery-ui-rtl.css HTTP/1.0\" 500 684",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"OPTIONS /wp-content/uploads/2020/10/hoodie-green-1-300x300.jpg HTTP/1.0\" 300 3904",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"POST /wp-admin/includes/class-wp-ms-sites-list-table.php HTTP/1.1\" 500 2139",
                "192.168.0.17 - miguel [13/11/2020:12:30:25 +0000] \"PUT /wp-includes/js/tinymce/utils/editable_selects.js HTTP/1.0\" 400 859",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"POST /wp-includes/sodium_compat/src/Core/Base64/Original.php HTTP/1.0\" 500 4062",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"HEAD /wp-content/plugins/jetpack/modules/shortcodes/css HTTP/1.0\" 500 1907",

                // High Traffic Gauge (7.40 req. per sec.): High Traffic - hits = {30}, avg: 10.00, triggered: {13/11/2020:12:30:25 +0000 (1605270625000)}
                "chihuahua.logpulse.com - admin [13/11/2020:12:30:25 +0000] \"HEAD /wp-admin/includes/class-wp-ms-sites-list-table.php HTTP/2.0\" 200 320",

                "192.168.0.17 - admin [13/11/2020:12:30:25 +0000] \"PATCH /wp-content/plugins/woocommerce/templates/emails/admin-failed-order.php HTTP/1.1\" 300 3035",
                "127.0.0.1 - lina [13/11/2020:12:30:25 +0000] \"PATCH /wp-content/plugins/jetpack/modules/shortcodes/css HTTP/1.0\" 200 2693",
                "192.168.0.17 - admin [13/11/2020:12:30:25 +0000] \"DELETE /wp-includes/Requests/Exception/HTTP/305.php HTTP/1.1\" 300 2773",
                "192.168.0.17 - miguel [13/11/2020:12:30:25 +0000] \"OPTIONS /wp-content/plugins/woocommerce/assets/css/jquery-ui/jquery-ui-rtl.css HTTP/1.0\" 500 684",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"OPTIONS /wp-content/uploads/2020/10/hoodie-green-1-300x300.jpg HTTP/1.0\" 300 3904",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"POST /wp-admin/includes/class-wp-ms-sites-list-table.php HTTP/1.1\" 500 2139",
                "192.168.0.17 - miguel [13/11/2020:12:30:25 +0000] \"PUT /wp-includes/js/tinymce/utils/editable_selects.js HTTP/1.0\" 400 859",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"POST /wp-includes/sodium_compat/src/Core/Base64/Original.php HTTP/1.0\" 500 4062",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"HEAD /wp-content/plugins/jetpack/modules/shortcodes/css HTTP/1.0\" 500 1907",
                "chihuahua.logpulse.com - admin [13/11/2020:12:30:25 +0000] \"HEAD /wp-admin/includes/class-wp-ms-sites-list-table.php HTTP/2.0\" 200 320",
                "192.168.0.17 - admin [13/11/2020:12:30:25 +0000] \"PATCH /wp-content/plugins/woocommerce/templates/emails/admin-failed-order.php HTTP/1.1\" 300 3035",
                "127.0.0.1 - lina [13/11/2020:12:30:25 +0000] \"PATCH /wp-content/plugins/jetpack/modules/shortcodes/css HTTP/1.0\" 200 2693",
                "192.168.0.17 - admin [13/11/2020:12:30:25 +0000] \"DELETE /wp-includes/Requests/Exception/HTTP/305.php HTTP/1.1\" 300 2773",
                "192.168.0.17 - miguel [13/11/2020:12:30:25 +0000] \"OPTIONS /wp-content/plugins/woocommerce/assets/css/jquery-ui/jquery-ui-rtl.css HTTP/1.0\" 500 684",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"OPTIONS /wp-content/uploads/2020/10/hoodie-green-1-300x300.jpg HTTP/1.0\" 300 3904",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"POST /wp-admin/includes/class-wp-ms-sites-list-table.php HTTP/1.1\" 500 2139",
                "192.168.0.17 - miguel [13/11/2020:12:30:25 +0000] \"PUT /wp-includes/js/tinymce/utils/editable_selects.js HTTP/1.0\" 400 859",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"POST /wp-includes/sodium_compat/src/Core/Base64/Original.php HTTP/1.0\" 500 4062",
                "127.0.0.1 - admin [13/11/2020:12:30:25 +0000] \"HEAD /wp-content/plugins/jetpack/modules/shortcodes/css HTTP/1.0\" 500 1907",

                // 4 rps
                // avg: 9.00, sum: 54
                "127.0.0.1 - miguel [13/11/2020:12:30:26 +0000] \"GET /wp-content/plugins/jetpack/_inc/blocks/opentable HTTP/1.0\" 300 1777",
                "chihuahua.logpulse.com - miguel [13/11/2020:12:30:26 +0000] \"OPTIONS /wp-content/plugins/woocommerce/packages/woocommerce-blocks/assets/js/data/cart/test/selectors.js HTTP/1.0\" 300 3974",
                "chihuahua.logpulse.com - miguel [13/11/2020:12:30:26 +0000] \"POST /wp-content/plugins/jetpack/modules/widget-visibility/widget-conditions.php HTTP/2.0\" 300 2174",
                "127.0.0.1 - admin [13/11/2020:12:30:26 +0000] \"OPTIONS /wp-content/plugins/jetpack/modules/search.php HTTP/1.0\" 300 799")
                .map(CLFParser::parseLogLine)
                .collect(Collectors.toList());
    }

    @Test
    public void test_executeSchedule() {
        int alertPeriod = 2;
        double threshold = 7.4;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            HighTrafficGauge gauge = new HighTrafficGauge(ps, alertPeriod, threshold);
            gauge.executeSchedule(
                    logLines.get(0).getUTCTimestamp(),
                    logLines.get(logLines.size() - 1).getUTCTimestamp(),
                    logLines);
        }
        assertThat(baos.toString(StandardCharsets.UTF_8),
                is("""
                        High Traffic Gauge (7.40 req. per sec.): High Traffic - hits = {12}, avg: 7.50, triggered: {13/11/2020:12:30:22 +0000 (1605270622000)}
                        High Traffic Gauge (7.40 req. per sec.): Traffic is back to normal - hits = {21}, avg: 7.00, triggered: {13/11/2020:12:30:24 +0000 (1605270624000)}
                        High Traffic Gauge (7.40 req. per sec.): High Traffic - hits = {30}, avg: 10.00, triggered: {13/11/2020:12:30:25 +0000 (1605270625000)}
                        """
                ));
    }

    @Test
    public void test_running_average() {
        int[] sizes = {5, 10, 6, 2, 27, 4};
        double avg = Arrays.stream(sizes).reduce(Integer::sum).getAsInt() / (1.0 * sizes.length);
        double ravg = 0.0;
        int sum = 0;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            sum += s;
            ravg = sum / (i + 1.0);
        }
        assertThat(avg, is(ravg));
    }
}
