/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.sequences.shouldContainExactly

import java.io.File

class ModuleInfoTest : WordSpec({
    "parsePnpmList()" should {
        "handle normal PNPM output" {
            val input = File("src/test/assets/pnpm-list.json").readText()

            val expectedResults = sequenceOf(
                listOf(
                    ModuleInfo(
                        name = "some-project",
                        version = "1.0.0",
                        path = "/tmp/work/root",
                        private = false,
                        dependencies = mapOf(
                            "eslint-scope" to ModuleInfo.Dependency(
                                from = "eslint-scope",
                                version = "link:node_modules/.pnpm/eslint-scope@5.1.1/node_modules/eslint-scope",
                                path = "/tmp/work/root/node_modules/.pnpm/eslint-scope@5.1.1/node_modules/eslint-scope"
                            )
                        )
                    ),
                    ModuleInfo(
                        name = "other-project",
                        version = "1.0.1",
                        path = "/tmp/work/other_root",
                        private = false,
                        dependencies = mapOf(
                            "@types/eslint" to ModuleInfo.Dependency(
                                from = "@types/eslint",
                                version = "link:node_modules/.pnpm/@types+eslint@8.56.2/node_modules/@types/eslint",
                                path = "/tmp/work/other_root/node_modules/.pnpm/@types+eslint@8.56.2" +
                                    "/node_modules/@types/eslint"
                            )
                        )
                    )
                )
            )

            val moduleInfos = parsePnpmList(input)

            moduleInfos shouldContainExactly expectedResults
        }

        "handle multiple JSON arrays" {
            val input = File("src/test/assets/pnpm-multi-list.json").readText()

            val expectedResults = sequenceOf(
                listOf(
                    ModuleInfo(
                        name = "outer-project",
                        version = "1.0.0",
                        path = "/tmp/work/top",
                        private = false,
                        dependencies = mapOf(
                            "eslint-scope" to ModuleInfo.Dependency(
                                from = "eslint-scope",
                                version = "link:node_modules/.pnpm/eslint-scope@5.1.1/node_modules/eslint-scope",
                                path = "/tmp/work/top/node_modules/.pnpm/eslint-scope@5.1.1/node_modules/eslint-scope"
                            )
                        )
                    )
                ),
                listOf(
                    ModuleInfo(
                        name = "nested-project",
                        version = "1.0.0",
                        path = "/tmp/work/top/nested",
                        private = false
                    )
                )
            )

            val moduleInfos = parsePnpmList(input)

            moduleInfos shouldContainExactly expectedResults
        }
    }
})
