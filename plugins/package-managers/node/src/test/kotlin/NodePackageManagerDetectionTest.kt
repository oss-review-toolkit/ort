/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType.NPM
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType.PNPM
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType.YARN
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType.YARN2
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.NpmFactory
import org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm.PnpmFactory
import org.ossreviewtoolkit.plugins.packagemanagers.node.yarn.YarnFactory
import org.ossreviewtoolkit.plugins.packagemanagers.node.yarn2.Yarn2Factory
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.test.getAssetFile

class NodePackageManagerDetectionTest : WordSpec({
    val packageManagers = listOf(NpmFactory(), PnpmFactory(), YarnFactory(), Yarn2Factory())
        .map { it.create(PluginConfig.EMPTY) }

    "All Node package manager detections" should {
        "ignore empty lockfiles" {
            NodePackageManagerType.entries.forAll {
                val lockfile = tempdir().resolve(it.lockfileName).apply {
                    writeText("")
                }

                it.hasLockfile(lockfile.parentFile) shouldBe false
            }
        }

        "ignore empty workspace files" {
            NodePackageManagerType.entries.forAll {
                val workspaceFile = tempdir().resolve(it.workspaceFileName).apply {
                    writeText("")
                }

                it.getWorkspaces(workspaceFile) should beNull()
            }
        }
    }

    "Detection for directories" should {
        "return all managers if only the definition file is present" {
            val projectDir = tempdir().apply {
                resolve("package.json").writeText("{}")
            }

            NodePackageManagerType.forDirectory(projectDir) shouldContainExactlyInAnyOrder
                NodePackageManagerType.entries
        }

        "return only those managers whose lockfiles are present" {
            val projectDir = tempdir().apply {
                resolve("package.json").writeText("{}")
                resolve(NPM.lockfileName).writeText("{}")
                resolve(PNPM.lockfileName).writeText("#")
            }

            NodePackageManagerType.forDirectory(projectDir) should containExactlyInAnyOrder(NPM, PNPM)
        }

        "return only NPM if distinguished by lockfile" {
            val projectDir = tempdir().apply {
                resolve("package.json").writeText("{}")
                resolve(NPM.lockfileName).writeText("{}")
            }

            NodePackageManagerType.forDirectory(projectDir) should containExactlyInAnyOrder(NPM)
        }

        "return only NPM if distinguished by other file" {
            val projectDir = tempdir().apply {
                resolve("package.json").writeText("{}")
                NPM.markerFileName?.also { resolve(it).writeText("{}") }
            }

            NodePackageManagerType.forDirectory(projectDir) should containExactlyInAnyOrder(NPM)
        }

        "return only PNPM if distinguished by lockfile" {
            val projectDir = tempdir().apply {
                resolve("package.json").writeText("{}")
                resolve(PNPM.lockfileName).writeText("#")
            }

            NodePackageManagerType.forDirectory(projectDir) should containExactlyInAnyOrder(PNPM)
        }

        "return only PNPM if distinguished by workspace file" {
            val projectDir = tempdir().apply {
                resolve("package.json").writeText("{}")
                resolve(PNPM.workspaceFileName).writeText("#")
            }

            NodePackageManagerType.forDirectory(projectDir) should containExactlyInAnyOrder(PNPM)
        }

        "return only YARN if distinguished by lockfile" {
            val projectDir = tempdir().apply {
                resolve("package.json").writeText("{}")
                resolve(YARN.lockfileName).writeText(YARN_LOCK_FILE_HEADER)
            }

            NodePackageManagerType.forDirectory(projectDir) should containExactlyInAnyOrder(YARN)
        }

        "return only YARN2 if distinguished by lockfile" {
            val projectDir = tempdir().apply {
                resolve("package.json").writeText("{}")
                resolve(YARN2.lockfileName).writeText(YARN2_LOCK_FILE_HEADER)
            }

            NodePackageManagerType.forDirectory(projectDir) should containExactlyInAnyOrder(YARN2)
        }
    }

    "NPM detection" should {
        "recognize lockfiles" {
            val lockfile = tempdir().resolve(NPM.lockfileName).apply {
                writeText("{}")
            }

            NPM.hasLockfile(lockfile.parentFile) shouldBe true
        }

        "parse workspace files" {
            val projectDir = getAssetFile("projects/synthetic/npm/workspaces")

            NPM.getWorkspaces(projectDir) shouldNotBeNull {
                mapNotNull { it.withoutPrefix(projectDir.invariantSeparatorsPath) } should
                    containExactly("/packages/**", "/apps/**")
            }
        }

        "filter definition files correctly" {
            val projectDir = getAssetFile("projects/synthetic")
            val definitionFiles = PackageManager.findManagedFiles(projectDir, packageManagers).values.flatten().toSet()

            val filteredFiles = NodePackageManagerDetection(definitionFiles).filterApplicable(NPM)

            filteredFiles.map { it.relativeTo(projectDir).invariantSeparatorsPath } should containExactlyInAnyOrder(
                "npm/no-lockfile/package.json",
                "npm/node-modules/package.json",
                "npm/project-with-lockfile/package.json",
                "npm/shrinkwrap/package.json",
                "npm/list-issues/package.json",
                "npm/babel/package.json",
                "npm/version-urls/package.json",
                "npm/workspaces/package.json"
            )
        }
    }

    "PNPM detection" should {
        "recognize lockfiles" {
            val lockfile = tempdir().resolve(PNPM.lockfileName).apply {
                writeText("lockfileVersion: '6.0'")
            }

            PNPM.hasLockfile(lockfile.parentFile) shouldBe true
        }

        "parse workspace files" {
            val projectDir = getAssetFile("projects/synthetic/pnpm/workspaces")

            PNPM.getWorkspaces(projectDir) shouldNotBeNull {
                mapNotNull { it.withoutPrefix(projectDir.invariantSeparatorsPath) } should
                    containExactly("/src/app/", "/src/packages/**")
            }
        }

        "filter definition files correctly" {
            val projectDir = getAssetFile("projects/synthetic")
            val definitionFiles = PackageManager.findManagedFiles(projectDir, packageManagers).values.flatten().toSet()

            val filteredFiles = NodePackageManagerDetection(definitionFiles).filterApplicable(PNPM)

            filteredFiles.map { it.relativeTo(projectDir).invariantSeparatorsPath } should containExactlyInAnyOrder(
                "pnpm/babel/package.json",
                "pnpm/project-with-lockfile/package.json",
                "pnpm/workspaces/package.json",
                "pnpm/workspaces/src/non-workspace/package-c/package.json",
                "pnpm/nested-project/package.json",
                "pnpm/nested-project/sub/package.json"
            )
        }
    }

    "Yarn detection" should {
        "recognize lockfiles" {
            val lockfile = tempdir().resolve(YARN.lockfileName).apply {
                writeText(YARN_LOCK_FILE_HEADER)
            }

            YARN.hasLockfile(lockfile.parentFile) shouldBe true
        }

        "parse workspace files" {
            val projectDir = getAssetFile("projects/synthetic/yarn/workspaces")

            YARN.getWorkspaces(projectDir) shouldNotBeNull {
                mapNotNull { it.withoutPrefix(projectDir.invariantSeparatorsPath) } should
                    containExactly("/packages/**")
            }
        }

        "filter definition files correctly" {
            val projectDir = getAssetFile("projects/synthetic")
            val definitionFiles = PackageManager.findManagedFiles(projectDir, packageManagers).values.flatten().toSet()

            val filteredFiles = NodePackageManagerDetection(definitionFiles).filterApplicable(YARN)

            filteredFiles.map { it.relativeTo(projectDir).invariantSeparatorsPath } should containExactlyInAnyOrder(
                "yarn/alias-use-for-transitive-deps/package.json",
                "yarn/babel/package.json",
                "yarn/dangling-linked-references/package.json",
                "yarn/invalid-package-json/package.json",
                "yarn/project-with-lockfile/package.json",
                "yarn/workspaces/package.json"
            )
        }
    }

    "Yarn2 detection" should {
        "recognize lockfiles" {
            val lockfile = tempdir().resolve(YARN2.lockfileName).apply {
                writeText(YARN2_LOCK_FILE_HEADER)
            }

            YARN2.hasLockfile(lockfile.parentFile) shouldBe true
        }

        "parse workspace files" {
            val projectDir = getAssetFile("projects/synthetic/yarn2/workspaces")

            YARN2.getWorkspaces(projectDir) shouldNotBeNull {
                mapNotNull { it.withoutPrefix(projectDir.invariantSeparatorsPath) } should
                    containExactly("/packages/**")
            }
        }

        "filter definition files correctly" {
            val projectDir = getAssetFile("projects/synthetic")
            val definitionFiles = PackageManager.findManagedFiles(projectDir, packageManagers).values.flatten().toSet()

            val filteredFiles = NodePackageManagerDetection(definitionFiles).filterApplicable(YARN2)

            filteredFiles.map { it.relativeTo(projectDir).invariantSeparatorsPath } should containExactlyInAnyOrder(
                "yarn2/project-with-lockfile/package.json",
                "yarn2/workspaces/package.json"
            )
        }
    }
})

private val YARN_LOCK_FILE_HEADER = """
    # THIS IS AN AUTOGENERATED FILE. DO NOT EDIT THIS FILE DIRECTLY.
    # yarn lockfile v1
""".trimIndent()

private val YARN2_LOCK_FILE_HEADER = """
    # This file is generated by running "yarn install" inside your project.
    # Manual changes might be lost - proceed with caution!

    __metadata:
""".trimIndent()
