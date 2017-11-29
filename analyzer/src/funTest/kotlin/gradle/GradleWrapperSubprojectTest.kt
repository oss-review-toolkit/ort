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
import com.here.ort.model.RemoteArtifact

import java.io.File

class GradleWrapperSubprojectTest : BaseGradleSpec() {
    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "org.gradle",
            name = "wrapper",
            version = "",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.createEmpty(),
            vcsProvider = "Git",
            vcsUrl = "https://github.com/gradle/gradle.git",
            vcsRevision = "e4f4804807ef7c2829da51877861ff06e07e006d",
            vcsPath = "subprojects/wrapper/"
    )

    override val expectedResultsDir = "src/funTest/assets/projects/external/gradle-submodules"

    override val expectedResultsDirsMap = mapOf(
            "$expectedResultsDir/subprojects/wrapper/wrapper-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/wrapper" +
                                    "/expected-wrapper-gradle-dependencies.yml")
    )
}
