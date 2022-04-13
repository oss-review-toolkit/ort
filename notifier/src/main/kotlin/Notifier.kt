/*
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.notifier

import java.time.Instant

import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.scriptsInstancesSharing
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

import org.ossreviewtoolkit.model.NotifierRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.NotifierConfiguration
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.notifier.modules.JiraNotifier
import org.ossreviewtoolkit.notifier.modules.MailNotifier
import org.ossreviewtoolkit.utils.scripting.ScriptRunner

class Notifier(
    ortResult: OrtResult = OrtResult.EMPTY,
    config: NotifierConfiguration = NotifierConfiguration(),
    resolutionProvider: ResolutionProvider = DefaultResolutionProvider()
) : ScriptRunner() {
    private val customProperties = buildMap {
        config.mail?.let { put("mailClient", MailNotifier(it)) }
        config.jira?.let { put("jiraClient", JiraNotifier(it)) }

        put("resolutionProvider", resolutionProvider)
    }

    override val compConfig = createJvmCompilationConfigurationFromTemplate<NotificationsScriptTemplate> {
        providedProperties(customProperties.mapValues { (_, v) -> KotlinType(v::class) })
    }

    override val evalConfig = ScriptEvaluationConfiguration {
        constructorArgs(ortResult)
        scriptsInstancesSharing(true)

        providedProperties(customProperties)
    }

    fun run(script: String): NotifierRun {
        val startTime = Instant.now()
        runScript(script)
        val endTime = Instant.now()

        return NotifierRun(startTime, endTime)
    }
}
