/*
 * Licensed to Miguel Arregui ("marregui") under one or more contributor
 * license agreements. See the LICENSE file distributed with this work
 * for additional information regarding copyright ownership. You may
 * obtain a copy at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * Copyright 2020, Miguel Arregui a.k.a. marregui
 */

object Versions {
    // logging
    const val slf4jApi = "2.0.0-alpha7"

    // testing
    const val junitJupiter = "5.8.2"
    const val hamcrest = "2.2"
    const val mockitoCore = "4.5.1"
}

object Libs {
    const val slf4jApi = "org.slf4j:slf4j-api:${Versions.slf4jApi}"
    const val slf4jLog4jBinding = "org.slf4j:slf4j-log4j12:${Versions.slf4jApi}"
    const val junitJupiter = "org.junit.jupiter:junit-jupiter:${Versions.junitJupiter}"
    const val junitJupiterApi = "org.junit.jupiter:junit-jupiter-api:${Versions.junitJupiter}"
    const val junitJupiterEngine = "org.junit.jupiter:junit-jupiter-engine:${Versions.junitJupiter}"
    const val hamcrest = "org.hamcrest:hamcrest:${Versions.hamcrest}"
    const val mockitoCore = "org.mockito:mockito-core:${Versions.mockitoCore}"
}
