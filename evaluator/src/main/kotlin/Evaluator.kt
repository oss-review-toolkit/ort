/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.evaluator

import com.here.ort.model.EvaluatorRun
import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.utils.ScriptRunner

import java.time.Instant

class Evaluator(ortResult: OrtResult) : ScriptRunner() {
    override val preface = """
            import com.here.ort.model.*
            import com.here.ort.model.config.*
            import com.here.ort.spdx.*
            import com.here.ort.utils.*

            import java.util.*

            // Input:
            val ortResult = bindings["ortResult"] as OrtResult

            // Output:
            val evalErrors = mutableListOf<OrtIssue>()

        """.trimIndent()

    override val postface = """

            evalErrors
        """.trimIndent()

    init {
        engine.put("ortResult", ortResult)
    }

    override fun run(script: String): EvaluatorRun {
        val startTime = Instant.now()

        @Suppress("UNCHECKED_CAST")
        val errors = super.run(script) as List<OrtIssue>

        val endTime = Instant.now()

        return EvaluatorRun(startTime, endTime, errors)
    }
}
