/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.utils.scripting

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver

@KotlinScript(
    displayName = "ORT Evaluator Rules Script",
    fileExtension = "rules.kts",
    compilationConfiguration = RulesCompilationConfiguration::class
)
open class RulesScriptTemplate(
    val ortResult: OrtResult = OrtResult.EMPTY,
    val licenseInfoResolver: LicenseInfoResolver = OrtResult.EMPTY.createLicenseInfoResolver(),
    val licenseClassifications: LicenseClassifications = LicenseClassifications()
) {
    val ruleViolations = mutableListOf<RuleViolation>()
}

class RulesCompilationConfiguration : ScriptCompilationConfiguration({
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

    defaultImports(
        "org.ossreviewtoolkit.evaluator.*",
        "org.ossreviewtoolkit.model.*",
        "org.ossreviewtoolkit.model.config.*",
        "org.ossreviewtoolkit.model.licenses.*",
        "org.ossreviewtoolkit.model.utils.*",
        "org.ossreviewtoolkit.utils.common.*",
        "org.ossreviewtoolkit.utils.core.*",
        "org.ossreviewtoolkit.utils.spdx.*",
        "java.util.*"
    )

    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
})
