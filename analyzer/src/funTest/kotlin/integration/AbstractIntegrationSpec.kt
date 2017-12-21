/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.analyzer.ManagedProjectFiles
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Package
import com.here.ort.utils.Expensive

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNot
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.Spec
import io.kotlintest.specs.StringSpec

import java.io.File

abstract class AbstractIntegrationSpec : StringSpec() {

    /**
     * The software package to download.
     */
    protected abstract val pkg: Package

    /**
     * The definition files that are expected to be found by the [PackageManager].
     */
    protected abstract val expectedDefinitionFiles: ManagedProjectFiles

    /**
     * The definition files that shall be used for dependency resolution. Defaults to [expectedDefinitionFiles], but
     * can be reduced for large projects that have a lot of definition files to speed up the test.
     */
    protected open val definitionFilesForTest by lazy { expectedDefinitionFiles }

    /**
     * The directory where the source code of [pkg] was downloaded to.
     */
    protected lateinit var downloadDir: File

    /**
     * The instance needs to be shared between tests because otherwise the source code of [pkg] would have to be
     * downloaded once per test.
     */
    override val oneInstancePerTest = false

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        val outputDir = createTempDir()
        downloadDir = Main.download(pkg, outputDir)
        try {
            super.interceptSpec(context, spec)
        } finally {
            outputDir.deleteRecursively()
        }
    }

    init {
        "Source code was downloaded successfully" {
            val workingTree = VersionControlSystem.forDirectory(downloadDir)
            workingTree shouldNotBe null
            workingTree!!.isValid() shouldBe true
            workingTree.provider shouldBe pkg.vcs.provider
        }.config(tags = setOf(Expensive))

        "All package manager definition files are found" {
            val definitionFiles = PackageManager.findManagedFiles(downloadDir)

            definitionFiles.size shouldBe expectedDefinitionFiles.size
            definitionFiles.forEach { manager, files ->
                println("Verifying definition files for $manager.")

                val expectedFiles = expectedDefinitionFiles[manager]

                expectedFiles shouldNotBe null
                files.sorted().joinToString("\n") shouldBe expectedFiles!!.sorted().joinToString("\n")
            }
        }.config(tags = setOf(Expensive))

        "Analyzer creates one non-empty result per definition file" {
            definitionFilesForTest.forEach { manager, files ->
                println("Resolving $manager dependencies in $files.")
                val results = manager.create().resolveDependencies(downloadDir, files)

                results.size shouldBe files.size
                results.values.forEach { result ->
                    VersionControlSystem.forProvider(result.project.vcsProcessed.provider) shouldBe
                            VersionControlSystem.forProvider(pkg.vcs.provider)
                    result.project.vcsProcessed.url shouldBe pkg.vcs.url
                    result.project.scopes shouldNot beEmpty()
                    result.packages shouldNot beEmpty()
                    result.hasErrors() shouldBe false
                }
            }
        }.config(tags = setOf(Expensive))
    }
}
