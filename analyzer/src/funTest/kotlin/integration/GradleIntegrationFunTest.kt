/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.integration

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.managers.Gradle
import org.ossreviewtoolkit.analyzer.managers.Maven
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class GradleIntegrationFunTest : AbstractIntegrationSpec() {
    override val pkg = Package(
        id = Identifier(
            type = "Maven",
            namespace = "org.gradle",
            name = "Gradle",
            version = "6.3.0"
        ),
        declaredLicenses = sortedSetOf(),
        description = "",
        homepageUrl = "",
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/gradle/gradle.git",
            revision = "v6.3.0"
        )
    )

    override val expectedManagedFiles by lazy {
        // The Gradle project contains far too many definition files to list them all here. Use this tests to double
        // check that all of them are found, and that they are assigned to the correct package manager.
        val gradleFilenames = listOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")

        val gradleFiles = outputDir.walk().filterTo(mutableListOf()) {
            it.name in gradleFilenames
        }

        // In each directory only the first file contained in gradleFiles is used.
        gradleFiles.removeAll { file ->
            val preferentialGradleFilenames = gradleFilenames.subList(0, gradleFilenames.indexOf(file.name))
            preferentialGradleFilenames.any { file.resolveSibling(it) in gradleFiles }
        }

        val pomFiles = outputDir.walk().filterTo(mutableListOf()) {
            it.name == "pom.xml"
        }

        mapOf(
            Gradle.Factory() as PackageManagerFactory to gradleFiles,
            Maven.Factory() as PackageManagerFactory to pomFiles
        )
    }

    override val managedFilesForTest by lazy {
        mapOf(
            Gradle.Factory() as PackageManagerFactory to listOf(outputDir.resolve("buildSrc/build.gradle"))
        )
    }
}
