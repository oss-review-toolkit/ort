/*
 * Copyright (c) 2017 HERE Europe B.V.
 *
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
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.analyzer.integration

import com.here.ort.model.Package
import java.io.File

class GradleLoggingSubprojectTest : BaseGradleSpec() {
    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "org.gradle",
            name = "gradle_logging",
            version = "",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            downloadUrl = "",
            hash = "",
            hashAlgorithm = "",
            vcsProvider = "Git",
            vcsUrl = "https://github.com/gradle/gradle.git",
            vcsRevision = "e4f4804807ef7c2829da51877861ff06e07e006d",
            vcsPath = "subprojects/logging/"
    )

    override val expectedResultsDir = "src/funTest/assets/projects/external/gradle-submodules"

    override val expectedResultsDirsMap = mapOf(
            "$expectedResultsDir/subprojects/logging/src/integTest/resources/org/gradle/internal/logging" +
                    "/LoggingIntegrationTest/logging/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging" +
                                    "/expected-logging-integration-test-dependencies.yml"),
            "$expectedResultsDir/subprojects/logging/src/integTest/resources/org/gradle/internal/logging" +
                    "/LoggingIntegrationTest/logging/buildSrc/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging" +
                                    "/expected-logging-integration-test-build-src-dependencies.yml"),
            "$expectedResultsDir/subprojects/logging/src/integTest/resources/org/gradle/internal/logging" +
                    "/LoggingIntegrationTest/logging/nestedBuild/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging" +
                                    "/expected-logging-integration-test-nested-build-dependencies.yml "),
            "$expectedResultsDir/subprojects/logging/src/integTest/resources/org/gradle/internal/logging" +
                    "/LoggingIntegrationTest/logging/nestedBuild/buildSrc/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging" +
                                    "/expected-logging-integration-test-nested-build-build-src-dependencies.yml"),
            "$expectedResultsDir/subprojects/logging/src/integTest/resources/org/gradle/internal/logging" +
                    "/LoggingIntegrationTest/logging/project1/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging" +
                                    "/expected-logging-integration-test-project1-dependencies.yml"),
            "$expectedResultsDir/subprojects/logging/src/integTest/resources/org/gradle/internal/logging" +
                    "/LoggingIntegrationTest/logging/project2/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging" +
                                    "/expected-logging-integration-test-project2-dependencies.yml"),
            "$expectedResultsDir/subprojects/logging/src/integTest/resources/org/gradle/internal/logging" +
                    "/LoggingIntegrationTest/multiThreaded/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging" +
                                    "/expected-logging-integration-test-multi-threaded-dependencies.yml")
    )
}
