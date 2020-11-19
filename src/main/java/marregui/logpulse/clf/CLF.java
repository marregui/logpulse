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

import marregui.logpulse.WithUTCTimestamp;
import marregui.logpulse.UTCTimestamp;

import java.util.Objects;

/**
 * Common Logging Format, CLF.
 *
 * @see WithUTCTimestamp
 * @see CLFParser
 */
public class CLF implements WithUTCTimestamp {

    /**
     * HTTP request method.
     */
    public enum HTTPMethod {
        /**
         * Used to request data from a specified resource.
         */
        GET,
        /**
         * GET, but without the response body.
         */
        HEAD,
        /**
         * Used to send data to a server to create/update a resource.
         */
        POST,
        /**
         * POST (complete representation of the request), but PUT requests are idempotent.
         */
        PUT,
        /**
         * PUT (partial representation of the request), but requests are not idempotent.
         */
        PATCH,
        /**
         * Used to delete the specified resource.
         */
        DELETE,
        /**
         * Describes the communication options for the target resource.
         */
        OPTIONS
    }

    private String host;
    private String ident;
    private String authUser;
    private long timestamp;
    private HTTPMethod method;
    private String resource;
    private String version;
    private int status;
    private long bytes;

    private CLF() {
        // use the builder
    }

    /**
     * @return IP address, or host name, of the client (remote host)
     * that made the request to the server
     */
    public String getHost() {
        return host;
    }

    /**
     * @return RFC 1413 identity of the client. Usually "-".
     */
    public String getIdent() {
        return ident;
    }

    /**
     * @return userid of the user requesting the resource. Usually
     * "-" unless .htaccess has requested authentication
     */
    public String getAuthUser() {
        return authUser;
    }

    @Override
    public long getUTCTimestamp() {
        return timestamp;
    }

    /**
     * @return HTTP method part of the request
     */
    public HTTPMethod getMethod() {
        return method;
    }

    /**
     * @return the resource part of the request
     */
    public String getResource() {
        return resource;
    }

    /**
     * @return what's before the second '/' in the resource section,  e.g. "/pages/create" -&gt; "/pages"
     */
    public String getSection() {
        int i = resource.indexOf("/");
        if (i != -1) {
            int j = resource.indexOf("/", i + 1);
            if (j != -1) {
                return resource.substring(i, j);
            }
        }
        return null;
    }

    /**
     * @return the HTTP version part of the request
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return HTTP status part of the request (2xx successful,
     * 3xx redirection, 4xx client error, 5xx server error).
     */
    public int getStatus() {
        return status;
    }

    /**
     * @return size of the object returned to the client
     */
    public long getBytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CLF)) {
            return false;
        }
        CLF other = (CLF) o;
        return bytes == other.bytes &&
                status == other.status &&
                method == other.method &&
                timestamp == other.timestamp &&
                host.equals(other.host) &&
                ident.equals(other.ident) &&
                authUser.equals(other.authUser) &&
                resource.equals(other.resource) &&
                version.equals(other.version);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result += result * 31 + bytes;
        result += result * 31 + status;
        result += result * 31 + method.name().hashCode();
        result += result * 31 + Long.hashCode(timestamp);
        result += result * 31 + host.hashCode();
        result += result * 31 + ident.hashCode();
        result += result * 31 + authUser.hashCode();
        result += result * 31 + resource.hashCode();
        result += result * 31 + version.hashCode();
        return result;
    }

    /**
     * <b>NOTE</b>: the result of this method should be a valid parameter for
     * {@link CLFParser#parseLogLine(CharSequence) }
     *
     * @return UTF-8 encoded CLF representation
     */
    @Override
    public String toString() {
        return String.format(
                "%s %s %s [%s] \"%s %s HTTP/%s\" %d %d",
                host, ident, authUser,
                UTCTimestamp.format(timestamp),
                method, resource, version,
                Integer.valueOf(status),
                Long.valueOf(bytes));
    }

    /**
     * @return an instance of a CLF builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Reusable builder (needs to be reset between calls to build).
     */
    public static class Builder {

        private CLF instance;
        private boolean isBuilt;

        private Builder() {
            instance();
        }

        private void instance() {
            instance = new CLF();
            instance.host = "-";
            instance.ident = "-";
            instance.authUser = "-";
            instance.timestamp = -1;
            instance.method = HTTPMethod.GET;
            instance.resource = "-";
            instance.version = "-";
            instance.status = -1;
            instance.bytes = -1L;
            isBuilt = false;
        }

        /**
         * @param host IP address, or host name, of the client (remote host)
         *             that made the request to the server
         * @return the builder, for method chaining
         */
        public Builder host(String host) {
            checkNotBuilt();
            instance.host = Objects.requireNonNull(host);
            return this;
        }

        /**
         * @param ident RFC 1413 identity of the client. Usually "-"
         * @return the builder, for method chaining
         */
        public Builder ident(String ident) {
            checkNotBuilt();
            instance.ident = Objects.requireNonNull(ident);
            return this;
        }

        /**
         * @param authUser userid of the user requesting the resource. Usually
         *                 "-" unless .htaccess has requested authentication
         * @return the builder, for method chaining
         */
        public Builder authUser(String authUser) {
            checkNotBuilt();
            instance.authUser = Objects.requireNonNull(authUser);
            return this;
        }

        /**
         * @param timestamp UTC Epoch representing the moment when the
         *                  log was produced
         * @return the builder, for method chaining
         */
        public Builder timestamp(long timestamp) {
            checkNotBuilt();
            instance.timestamp = timestamp;
            return this;
        }

        /**
         * @param method HTTP method part of the request
         * @return the builder, for method chaining
         */
        public Builder method(HTTPMethod method) {
            checkNotBuilt();
            instance.method = method;
            return this;
        }

        /**
         * @param resource resource part of the request
         * @return the builder, for method chaining
         */
        public Builder resource(String resource) {
            checkNotBuilt();
            instance.resource = Objects.requireNonNull(resource);
            return this;
        }

        /**
         * @param version HTTP version part of the request
         * @return the builder, for method chaining
         */
        public Builder version(String version) {
            checkNotBuilt();
            instance.version = Objects.requireNonNull(version);
            return this;
        }

        /**
         * @param status HTTP status part of the request (2xx successful,
         *               3xx redirection, 4xx client error, 5xx server error)
         * @return the builder, for method chaining
         */
        public Builder status(int status) {
            checkNotBuilt();
            instance.status = status;
            return this;
        }

        /**
         * @param bytes size of the object returned to the client
         * @return the builder, for method chaining
         */
        public Builder bytes(long bytes) {
            checkNotBuilt();
            instance.bytes = bytes;
            return this;
        }

        /**
         * It can be called once at most, unless reset is called
         * in between successive calls of this method.
         *
         * @return a new instance
         */
        public CLF build() {
            checkNotBuilt();
            isBuilt = true;
            return instance;
        }

        /**
         * Resets the builder, getting it ready to build the next instance.
         */
        public void reset() {
            instance();
        }

        private void checkNotBuilt() {
            if (isBuilt) {
                throw new IllegalStateException("already built");
            }
        }
    }
}
