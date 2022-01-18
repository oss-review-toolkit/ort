/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.scriptsInstancesSharing
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.utils.scripting.ScriptRunner

class Evaluator(
    ortResult: OrtResult = OrtResult.EMPTY,
    licenseInfoResolver: LicenseInfoResolver = ortResult.createLicenseInfoResolver(),
    licenseClassifications: LicenseClassifications = LicenseClassifications(),
    time: Instant = Instant.now()
) : ScriptRunner() {
    override val compConfig = createJvmCompilationConfigurationFromTemplate<RulesScriptTemplate>()

    override val evalConfig = ScriptEvaluationConfiguration {
        constructorArgs(ortResult, licenseInfoResolver, licenseClassifications, time)
        scriptsInstancesSharing(true)
    }

    fun run(script: String): EvaluatorRun {
        val startTime = Instant.now()
        val scriptInstance = runScript(script).scriptInstance as RulesScriptTemplate
        val endTime = Instant.now()

        return EvaluatorRun(startTime, endTime, scriptInstance.ruleViolations)
    }
}
