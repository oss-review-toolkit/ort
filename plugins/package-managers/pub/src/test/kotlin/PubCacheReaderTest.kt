/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.pub

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.plugins.packagemanagers.pub.model.PackageInfo
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeMkdirs

private const val PACKAGE_NAME = "non-existing-package"
private const val PACKAGE_VERSION = "0.0.0"
private const val RESOLVED_REF = "0000000000000000000000000000000000000000"
private const val RELATIVE_PATH = ".."
private val ABSOLUTE_PATH = File(Os.env["PUB_CACHE"])

class PubCacheReaderTest : WordSpec({
    val tmpPubCacheDir = tempdir().also { Os.env["PUB_CACHE"] = it.absolutePath }
    val gitPackageCacheDir = tmpPubCacheDir.resolve("git/$PACKAGE_NAME-$RESOLVED_REF")
    val gitPackageWithPathCacheDir = tmpPubCacheDir.resolve("git/$PACKAGE_NAME-$RESOLVED_REF/$PACKAGE_NAME")
    val hostedPackageCacheDir = tmpPubCacheDir.resolve("hosted/oss-review-toolkit.org/$PACKAGE_NAME-$PACKAGE_VERSION")
    val customPackageCacheDir = tmpPubCacheDir.resolve(
        "hosted/oss-review-toolkit.org%47api%47pub%47repository%47/$PACKAGE_NAME-$PACKAGE_VERSION"
    )
    val localPackagePathAbsolute = ABSOLUTE_PATH
    val localPackagePathRelative = ABSOLUTE_PATH.resolve(RELATIVE_PATH)

    val reader = PubCacheReader()

    beforeSpec {
        gitPackageCacheDir.safeMkdirs()
        gitPackageWithPathCacheDir.safeMkdirs()
        hostedPackageCacheDir.safeMkdirs()
        customPackageCacheDir.safeMkdirs()
    }

    "findProjectRoot()" should {
        "resolve the path of a Git dependency without path" {
            val packageInfo = PackageInfo(
                dependency = "direct main",
                description = PackageInfo.Description(
                    resolvedRef = RESOLVED_REF,
                    url = "https://github.com/oss-review-toolkit/$PACKAGE_NAME.git"
                ),
                source = "git",
                version = "9.9.9"
            )

            reader.findProjectRoot(packageInfo, ABSOLUTE_PATH) shouldBe gitPackageCacheDir
        }

        "resolve the path of a Git dependency with path" {
            val packageInfo = PackageInfo(
                dependency = "direct main",
                description = PackageInfo.Description(
                    path = PACKAGE_NAME,
                    resolvedRef = RESOLVED_REF,
                    url = "https://github.com/oss-review-toolkit/$PACKAGE_NAME.git"
                ),
                source = "git",
                version = "9.9.9"
            )

            reader.findProjectRoot(packageInfo, ABSOLUTE_PATH) shouldBe gitPackageWithPathCacheDir
        }

        "resolve the path of a Git dependency with special path" {
            val packageInfo = PackageInfo(
                dependency = "direct main",
                description = PackageInfo.Description(
                    path = ".",
                    resolvedRef = RESOLVED_REF,
                    url = "https://github.com/oss-review-toolkit/$PACKAGE_NAME.git"
                ),
                source = "git",
                version = "9.9.9"
            )

            reader.findProjectRoot(packageInfo, ABSOLUTE_PATH) shouldBe gitPackageCacheDir
        }

        "resolve the path of a hosted dependency" {
            val packageInfo = PackageInfo(
                dependency = "transitive",
                description = PackageInfo.Description(
                    name = PACKAGE_NAME,
                    url = "https://oss-review-toolkit.org"
                ),
                source = "hosted",
                version = PACKAGE_VERSION
            )

            reader.findProjectRoot(packageInfo, ABSOLUTE_PATH) shouldBe hostedPackageCacheDir
        }

        "resolve the path of a custom package repository dependency" {
            val packageInfo = PackageInfo(
                dependency = "transitive",
                description = PackageInfo.Description(
                    name = PACKAGE_NAME,
                    url = "https://oss-review-toolkit.org/api/pub/repository/"
                ),
                source = "hosted",
                version = PACKAGE_VERSION
            )

            reader.findProjectRoot(packageInfo, ABSOLUTE_PATH) shouldBe customPackageCacheDir
        }

        "resolve the relative path of a local dependency" {
            val packageInfo = PackageInfo(
                dependency = "transitive",
                description = PackageInfo.Description(
                    path = RELATIVE_PATH,
                    relative = true
                ),
                source = "path"
            )

            reader.findProjectRoot(packageInfo, ABSOLUTE_PATH) shouldBe localPackagePathRelative
        }

        "resolve the absolute path of a local dependency" {
            val packageInfo = PackageInfo(
                dependency = "transitive",
                description = PackageInfo.Description(
                    path = ABSOLUTE_PATH.absolutePath,
                    relative = false
                ),
                source = "path"
            )

            reader.findProjectRoot(packageInfo, ABSOLUTE_PATH) shouldBe localPackagePathAbsolute
        }
    }
})
