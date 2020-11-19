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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

public class CLFBuilderTest {

    @Test
    public void test_create_default_instance() {
        CLF clf = CLF.builder().build();
        assertThat(clf.getHost(), is("-"));
        assertThat(clf.getIdent(), is("-"));
        assertThat(clf.getAuthUser(), is("-"));
        assertThat(clf.getUTCTimestamp(), is(-1L));
        assertThat(clf.getMethod(), is(CLF.HTTPMethod.GET));
        assertThat(clf.getResource(), is("-"));
        assertThat(clf.getVersion(), is("-"));
        assertThat(clf.getStatus(), is(-1));
        assertThat(clf.getBytes(), is(-1L));
    }

    @Test
    public void test_builder_can_only_be_used_once() {
        CLF.Builder builder = CLF.builder();
        builder.build();
        assertThrows(IllegalStateException.class, builder::build, "already built");
    }

    @Test
    public void test_builder_cannot_be_set_once_built() {
        CLF.Builder builder = CLF.builder();
        builder.build();
        assertThrows(IllegalStateException.class, () -> builder.ident("ident"), "already built");
    }

    @Test
    public void test_builder_can_be_reset_for_reuse() {
        CLF.Builder builder = CLF.builder();
        CLF clf1 = builder.build();
        builder.reset();
        CLF clf2 = builder.build();
        assertThat(clf1, is(clf2));
        assertNotSame(clf1, clf2);
    }

    @Test
    public void test_to_string_on_clf_instances() {
        String logLine = CLF.builder()
                .host("127.0.0.1")
                .ident("-")
                .authUser("miguel")
                .timestamp(1234567890L)
                .method(CLF.HTTPMethod.GET)
                .resource("/resource")
                .version("1.1")
                .status(200)
                .bytes(999L)
                .build()
                .toString();
        assertThat(logLine, is("127.0.0.1 - miguel [15/01/1970:06:56:07 +0000] \"GET /resource HTTP/1.1\" 200 999"));
    }

    @Test
    public void test_equals_hashcode_on_clf_instances() {
        CLF log = CLF.builder()
                .host("127.0.0.1")
                .ident("-")
                .authUser("miguel")
                .timestamp(1234567890L)
                .method(CLF.HTTPMethod.GET)
                .resource("/resource")
                .version("1.1")
                .status(200)
                .bytes(999L).build();
        CLF logAgain = CLF.builder()
                .host("127.0.0.1")
                .ident("-")
                .authUser("miguel")
                .timestamp(1234567890L)
                .method(CLF.HTTPMethod.GET)
                .resource("/resource")
                .version("1.1")
                .status(200)
                .bytes(999L).build();
        assertThat(log, is(logAgain));
        assertThat(log.hashCode(), is(logAgain.hashCode()));
    }
}
