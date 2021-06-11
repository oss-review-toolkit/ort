/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.utils.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class SpdxDocumentFileFunTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/spdx").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Project dependencies are detected correctly" {
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("spdx-project-expected-output.yml"),
                url = vcsUrl,
                urlProcessed = normalizeVcsUrl(vcsUrl),
                revision = vcsRevision
            )

            val definitionFile = projectDir.resolve("project/project.spdx.yml")
            val actualResult = createSpdxDocumentFile().resolveSingleProject(definitionFile).toYaml()

            actualResult shouldBe expectedResult
        }

        "mapDefinitionFiles() removes SPDX documents that do not describe a project" {
            val projectFile = projectDir.resolve("project/project.spdx.yml")
            val packageFile = projectDir.resolve("package/libs/curl/package.spdx.yml")

            val definitionFiles = listOf(projectFile, packageFile)

            val result = createSpdxDocumentFile().mapDefinitionFiles(definitionFiles)

            result should containExactly(projectFile)
        }

        // TODO: Test that we can read in files written by SpdxDocumentReporter.
    }

    private fun createSpdxDocumentFile() =
        SpdxDocumentFile(
            "SpdxDocumentFile",
            USER_DIR,
            DEFAULT_ANALYZER_CONFIGURATION,
            DEFAULT_REPOSITORY_CONFIGURATION
        )
}
