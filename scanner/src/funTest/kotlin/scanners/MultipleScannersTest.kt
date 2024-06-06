/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.scanners

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

private val PROJECT_ID = Identifier("Dummy", "", "project", "1.0.0")
private val PACKAGE_ID = Identifier("Dummy", "", "pkg1", "1.0.0")

class MultipleScannersTest : WordSpec({
    "Scanning a project and a package with overlapping provenance and non overlapping scanners" should {
        val analyzerResult = createAnalyzerResult()

        val scannerWrappers = mapOf(
            PackageType.PROJECT to listOf(DummyScanner("Dummy2")),
            PackageType.PACKAGE to listOf(DummyScanner("Dummy1"))
        )
        val ortResult = createScanner(scannerWrappers).scan(analyzerResult, skipExcluded = false, emptyMap())

        "return scan results with non-overlapping scanners" {
            ortResult.scanner shouldNotBeNull {
                val results = getScanResults(PROJECT_ID)
                results.map { it.scanner.name }.toSet() should containExactly("Dummy2")
                val results2 = getScanResults(PACKAGE_ID)
                results2.map { it.scanner.name }.toSet() should containExactly("Dummy1")
            }
        }
    }
})

private fun createAnalyzerResult(): OrtResult {
    val vcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git",
        revision = "97d57bb4795bc41f496e1a8e2c7751cefc7da7ec",
        path = ""
    )

    val pkg = Package.EMPTY.copy(
        id = PACKAGE_ID,
        vcs = vcs,
        vcsProcessed = vcs.normalize()
    )

    val project = Project.EMPTY.copy(
        id = PROJECT_ID,
        vcs = vcs,
        vcsProcessed = vcs.normalize()
    )

    val analyzerRun = AnalyzerRun.EMPTY.copy(
        result = AnalyzerResult.EMPTY.copy(
            projects = setOf(project),
            packages = setOf(pkg)
        )
    )

    return OrtResult.EMPTY.copy(analyzer = analyzerRun)
}
