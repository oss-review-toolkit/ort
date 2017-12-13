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

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.analyzer.managers.Gradle
import com.here.ort.analyzer.managers.Maven
import com.here.ort.analyzer.managers.NPM
import com.here.ort.analyzer.managers.PIP
import com.here.ort.analyzer.managers.SBT
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo

import java.io.File

class OssReviewToolkitIntegrationTest : AbstractIntegrationSpec() {

    override val pkg: Package = Package(
            packageManager = "Gradle",
            namespace = "com.here.ort",
            name = "OSS Review Toolkit",
            version = "",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo(
                    provider = "Git",
                    url = "https://github.com/heremaps/oss-review-toolkit.git",
                    revision = "953faa00effe74bb87d9f7f10fd857f58bb1192f",
                    path = ""
            )
    )

    override val expectedDefinitionFiles by lazy {
        mapOf(
                Gradle to listOf(
                        File(downloadDir, "analyzer/build.gradle"),
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/all-managers/build.gradle"),
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/gradle/app/build.gradle"),
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/gradle/build.gradle"),
                        File(downloadDir,
                                "analyzer/src/funTest/assets/projects/synthetic/gradle/lib-without-repo/build.gradle"),
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/gradle/lib/build.gradle"),
                        File(downloadDir, "build.gradle"),
                        File(downloadDir, "downloader/build.gradle"),
                        File(downloadDir, "graph/build.gradle"),
                        File(downloadDir, "model/build.gradle"),
                        File(downloadDir, "scanner/build.gradle"),
                        File(downloadDir, "utils-test/build.gradle"),
                        File(downloadDir, "utils/build.gradle")
                ),
                Maven to listOf(
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/all-managers/pom.xml"),
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/maven/app/pom.xml"),
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/maven/lib/pom.xml"),
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/maven/pom.xml")
                ),
                NPM to listOf(
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/all-managers/package.json"),
                        File(downloadDir,
                                "analyzer/src/funTest/assets/projects/synthetic/npm/multiple-lockfiles/package.json"),
                        File(downloadDir,
                                "analyzer/src/funTest/assets/projects/synthetic/npm/no-lockfile/package.json"),
                        File(downloadDir,
                                "analyzer/src/funTest/assets/projects/synthetic/npm/node-modules/package.json"),
                        File(downloadDir,
                                "analyzer/src/funTest/assets/projects/synthetic/npm/package-lock/package.json"),
                        File(downloadDir,
                                "analyzer/src/funTest/assets/projects/synthetic/npm/shrinkwrap/package.json"),
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/npm/yarn/package.json")
                ),
                PIP to listOf(
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/all-managers/setup.py")
                ),
                SBT to listOf(
                        File(downloadDir, "analyzer/src/funTest/assets/projects/synthetic/all-managers/build.sbt")
                )
        )
    }

    override val definitionFilesForTest by lazy {
        mapOf(Gradle as PackageManagerFactory<PackageManager> to listOf(File(downloadDir, "analyzer/build.gradle")))
    }

}
