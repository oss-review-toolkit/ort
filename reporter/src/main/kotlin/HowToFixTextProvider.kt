/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.scriptsInstancesSharing
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.utils.scripting.ScriptRunner

/**
 * Provides how-to-fix texts in Markdown format for any given [Issue].
 */
fun interface HowToFixTextProvider {
    companion object {
        /**
         * A [HowToFixTextProvider] which returns null for any given [Issue].
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
    fun getHowToFixText(issue: Issue): String?
}

private class HowToFixScriptRunner(ortResult: OrtResult) : ScriptRunner() {
    override val compConfig = createJvmCompilationConfigurationFromTemplate<HowToFixTextProviderScriptTemplate>()

    override val evalConfig = ScriptEvaluationConfiguration {
        constructorArgs(ortResult)
        scriptsInstancesSharing(true)
    }

    fun run(script: String): HowToFixTextProvider {
        val scriptValue = runScript(script) as ResultValue.Value
        return scriptValue.value as HowToFixTextProvider
    }
}
