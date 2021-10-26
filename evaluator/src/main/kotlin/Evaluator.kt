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

import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.utils.core.ScriptRunner

class Evaluator(
    ortResult: OrtResult = OrtResult.EMPTY,
    licenseInfoResolver: LicenseInfoResolver = OrtResult.EMPTY.createLicenseInfoResolver(),
    licenseClassifications: LicenseClassifications = LicenseClassifications()
) : ScriptRunner() {
    override val preface = """
            import org.ossreviewtoolkit.evaluator.*
            import org.ossreviewtoolkit.model.*
            import org.ossreviewtoolkit.model.config.*
            import org.ossreviewtoolkit.model.licenses.*
            import org.ossreviewtoolkit.model.utils.*
            import org.ossreviewtoolkit.spdx.*
            import org.ossreviewtoolkit.utils.core.*

            import java.util.*

            // Output:
            val ruleViolations = mutableListOf<RuleViolation>()

        """.trimIndent()

    override val postface = """

            ruleViolations
        """.trimIndent()

    init {
        engine.put("ortResult", ortResult)
        engine.put("licenseInfoResolver", licenseInfoResolver)
        engine.put("licenseClassifications", licenseClassifications)
    }

    override fun run(script: String): EvaluatorRun {
        val startTime = Instant.now()

        @Suppress("UNCHECKED_CAST")
        val violations = super.run(script) as List<RuleViolation>

        val endTime = Instant.now()

        return EvaluatorRun(startTime, endTime, violations)
    }
}
