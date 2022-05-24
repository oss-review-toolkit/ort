/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter

import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.utils.common.ScriptRunner

/**
 * Provides how-to-fix texts in Markdown format for any given [OrtIssue].
 */
fun interface HowToFixTextProvider {
    companion object {
        /**
         * A [HowToFixTextProvider] which returns null for any given [OrtIssue].
         */
        val NONE = HowToFixTextProvider { null }

        /**
         * Return the [HowToFixTextProvider] which in-turn has to be returned by the given [script].
         */
        fun fromKotlinScript(script: String, ortResult: OrtResult): HowToFixTextProvider =
            HowToFixScriptRunner(ortResult).run(script)
    }

    /**
     * Return a Markdown text describing how to fix the given [issue]. Non-null return values override the default
     * how-to-fix texts, while a null value keeps the default.
     */
    fun getHowToFixText(issue: OrtIssue): String?
}

private class HowToFixScriptRunner(ortResult: OrtResult) : ScriptRunner() {
    override val preface = """
            import org.ossreviewtoolkit.model.*
            import org.ossreviewtoolkit.model.config.*
            import org.ossreviewtoolkit.model.licenses.*
            import org.ossreviewtoolkit.model.utils.*
            import org.ossreviewtoolkit.reporter.HowToFixTextProvider
            import org.ossreviewtoolkit.utils.common.*
            import org.ossreviewtoolkit.utils.ort.*

        """.trimIndent()

    override val postface = """
        """.trimIndent()

    init {
        engine.put("ortResult", ortResult)
    }

    override fun run(script: String): HowToFixTextProvider = super.run(script) as HowToFixTextProvider
}
