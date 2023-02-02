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

package org.ossreviewtoolkit.analyzer

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.Maven
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration

class AnalyzerTest : WordSpec({
    beforeTest {
        mockkObject(PackageManager)
        every { PackageManager.ALL } answers { callOriginal() }
        every { PackageManager.findManagedFiles(rootDir, any(), any()) } returns projectFiles
    }

    afterTest {
        unmockkObject(PackageManager)
    }

    "findManagedFiles" should {
        "find all managed files" {
            val analyzer = Analyzer(AnalyzerConfiguration())

            val result = analyzer.findManagedFiles(rootDir, repositoryConfiguration = repositoryConfig)

            checkManagedFileInfo(result)

            verify {
                PackageManager.findManagedFiles(rootDir, any(), Excludes.EMPTY)
            }
        }

        "take excludes into account if configured" {
            val analyzer = Analyzer(AnalyzerConfiguration(skipExcluded = true))

            val result = analyzer.findManagedFiles(rootDir, repositoryConfiguration = repositoryConfig)

            checkManagedFileInfo(result)

            verify {
                PackageManager.findManagedFiles(rootDir, any(), repositoryConfig.excludes)
            }
        }
    }
})

/** The root dir used when searching for definition files. */
private val rootDir = File("projectRoot").absoluteFile

/**
 * A default [RepositoryConfiguration] that also defines some [Excludes]. This is used to test whether [Excludes] are
 * correctly propagated to the [PackageManager].
 */
private val repositoryConfig = RepositoryConfiguration(
    excludes = Excludes(
        paths = listOf(PathExclude("**/src/test/**", PathExcludeReason.TEST_OF))
    )
)

/**
 * A [PackageManagerFactory] used as key in the default result for [PackageManager.findManagedFiles].
 */
private val factory: PackageManagerFactory = Maven.Factory()

/**
 * A default result to be returned from [PackageManager.findManagedFiles].
 */
private val projectFiles: ManagedProjectFiles =
    mapOf(factory to listOf(File("pom.xml"), File("sub/pom.xml")))

/**
 * Return a [Map] with the names of the [PackageManager]s contained in this map as keys to simplify access.
 */
private fun Map<PackageManager, List<File>>.byName(): Map<String, List<File>> = mapKeys { e -> e.key.managerName }

/**
 * Check whether the given [info] contains the expected information.
 */
private fun checkManagedFileInfo(info: Analyzer.ManagedFileInfo) {
    with(info) {
        absoluteProjectPath shouldBe rootDir
        repositoryConfiguration shouldBe repositoryConfig
        val managedFilesByName = managedFiles.byName()
        managedFilesByName.keys shouldContainOnly listOf("Maven")
        managedFilesByName.getValue("Maven") shouldBe projectFiles[factory]
    }
}
