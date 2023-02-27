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

package org.ossreviewtoolkit.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

class OrtImportOrderTest : WordSpec({
    val rule = OrtImportOrder(Config.empty)

    "OrtImportOrder rule" should {
        "succeed if the order is correct" {
            val findings = rule.lint(
                """
                import java.io.File
                import java.time.Instant
                
                import kotlinx.coroutines.CoroutineScope
                import kotlinx.coroutines.Dispatchers
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow
                import kotlinx.coroutines.flow.first
                import kotlinx.coroutines.launch
                import kotlinx.coroutines.sync.Mutex
                import kotlinx.coroutines.withContext
                
                import org.apache.logging.log4j.kotlin.Logging
                
                import org.ossreviewtoolkit.analyzer.PackageManager.Companion.excludes
                import org.ossreviewtoolkit.analyzer.managers.Unmanaged
                import org.ossreviewtoolkit.downloader.VersionControlSystem
                import org.ossreviewtoolkit.model.AnalyzerResult
                import org.ossreviewtoolkit.model.AnalyzerRun
                import org.ossreviewtoolkit.model.utils.PackageCurationProvider
                import org.ossreviewtoolkit.model.yamlMapper
                import org.ossreviewtoolkit.utils.common.CommandLineTool
                import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
                import org.ossreviewtoolkit.utils.ort.Environment                
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "fail if imports are not sorted alphabetically" {
            val findings = rule.lint(
                """
                import java.time.Instant
                import java.io.File
                """.trimIndent()
            )

            findings should haveSize(1)
        }

        "fail if an empty line between different top-level packages is missing" {
            val findings = rule.lint(
                """
                import java.time.Instant
                import kotlinx.coroutines.CoroutineScope
                """.trimIndent()
            )

            findings should haveSize(1)
        }
    }
})
