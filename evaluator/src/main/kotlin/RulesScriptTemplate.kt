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

package org.ossreviewtoolkit.evaluator

import java.time.Instant

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.utils.scripting.OrtScriptCompilationConfiguration

class RulesScriptCompilationConfiguration : ScriptCompilationConfiguration(
    OrtScriptCompilationConfiguration(),
    body = {
        defaultImports(
            "org.ossreviewtoolkit.evaluator.*",
            "org.ossreviewtoolkit.model.*",
            "org.ossreviewtoolkit.model.config.*",
            "org.ossreviewtoolkit.model.licenses.*",
            "org.ossreviewtoolkit.model.utils.*",
            "org.ossreviewtoolkit.utils.spdx.*"
        )
    }
)

@KotlinScript(
    displayName = "ORT Evaluator Rules Script",
    fileExtension = "rules.kts",
    compilationConfiguration = RulesScriptCompilationConfiguration::class
)
open class RulesScriptTemplate(
    val ortResult: OrtResult,
    val licenseInfoResolver: LicenseInfoResolver,
    val licenseClassifications: LicenseClassifications,
    val time: Instant
) {
    val ruleViolations = mutableListOf<RuleViolation>()
}
