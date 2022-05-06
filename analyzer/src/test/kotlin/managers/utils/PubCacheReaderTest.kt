/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.test.createSpecTempDir

private const val PACKAGE_NAME = "non-existing-package"
private const val PACKAGE_VERSION = "0.0.0"
private const val RESOLVED_REF = "0000000000000000000000000000000000000000"
private const val RELATIVE_PATH = ".."
private val ABSOLUTE_PATH = File(Os.env["PUB_CACHE"])

class PubCacheReaderTest : WordSpec({
    val tmpPubCacheDir = createSpecTempDir().also { Os.env["PUB_CACHE"] = it.absolutePath }
    val gitPackageCacheDir = tmpPubCacheDir.resolve("git/$PACKAGE_NAME-$RESOLVED_REF")
    val hostedPackageCacheDir = tmpPubCacheDir.resolve("hosted/oss-review-toolkit.org/$PACKAGE_NAME-$PACKAGE_VERSION")
    val localPackagePathAbsolute = ABSOLUTE_PATH
    val localPackagePathRelative = ABSOLUTE_PATH.resolve(RELATIVE_PATH)

    val reader = PubCacheReader()

    beforeSpec {
        gitPackageCacheDir.safeMkdirs()
        hostedPackageCacheDir.safeMkdirs()
    }

    "findProjectRoot" should {
        "resolve the path of a Git dependency" {
            reader.findProjectRoot(
                jsonMapper.readTree(
                    """
                        {
                            "dependency": "direct main",
                            "description": {
                                "path": ".",
                                "ref": "master",
                                "resolved-ref": "$RESOLVED_REF",
                                "url": "https://github.com/oss-review-toolkit/$PACKAGE_NAME.git"
                            },
                            "source": "git",
                            "version": "9.9.9"
                        }
                    """.trimIndent()
                ),
                ABSOLUTE_PATH // not used
            ) shouldBe gitPackageCacheDir
        }

        "resolve the path of a hosted dependency" {
            PubCacheReader().findProjectRoot(
                jsonMapper.readTree(
                    """
                        {
                            "dependency": "transitive",
                            "description": {
                                "name": "$PACKAGE_NAME",
                                "url": "https://oss-review-toolkit.org"
                            },
                            "source": "hosted",
                            "version": "$PACKAGE_VERSION"
                        }
                    """.trimIndent()
                ),
                ABSOLUTE_PATH // not used
            ) shouldBe hostedPackageCacheDir
        }

        "resolve the relative path of a local dependency" {
            PubCacheReader().findProjectRoot(
                jsonMapper.readTree(
                    """
                        {
                            "dependency": "transitive",
                            "description": {
                                "path": "$RELATIVE_PATH",
                                "relative": true
                            },
                            "source": "path"
                        }
                    """.trimIndent()
                ),
                ABSOLUTE_PATH
            ) shouldBe localPackagePathRelative
        }

        "resolve the absolute path of a local dependency" {
            PubCacheReader().findProjectRoot(
                jsonMapper.readTree(
                    """
                        {
                            "dependency": "transitive",
                            "description": {
                                "path": "$ABSOLUTE_PATH",
                                "relative": false
                            },
                            "source": "path"
                        }
                    """.trimIndent()
                ),
                ABSOLUTE_PATH
            ) shouldBe localPackagePathAbsolute
        }
    }
})
