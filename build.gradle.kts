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

import java.util.Date

plugins {
    id("java")
    id("idea")
}

group = "marregui.logpulse"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Libs is defined in buildSrc
dependencies {
    implementation(Libs.slf4jApi)
    implementation(Libs.slf4jLog4jBinding)
    testImplementation(Libs.junitJupiter)
    testImplementation(Libs.junitJupiterApi)
    testImplementation(Libs.junitJupiterEngine)
    testImplementation(Libs.hamcrest)
    testImplementation(Libs.mockitoCore)
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "marregui.logpulse.Main"
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] = project.version
        attributes["Build-Timestamp"] = Date().toString()
    }
    from(configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) })
    val sourcesMain = sourceSets.main.get()
    from(sourcesMain.output)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "7.4.2"
    distributionType = Wrapper.DistributionType.ALL
}
