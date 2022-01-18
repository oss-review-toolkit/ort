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

package org.ossreviewtoolkit.notifier

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.utils.scripting.OrtScriptCompilationConfiguration

class NotificationScriptCompilationConfiguration : ScriptCompilationConfiguration(
    OrtScriptCompilationConfiguration(),
    body = {
        defaultImports(
            "org.ossreviewtoolkit.model.*",
            "org.ossreviewtoolkit.model.config.*",
            "org.ossreviewtoolkit.model.licenses.*",
            "org.ossreviewtoolkit.model.utils.*",
            "org.ossreviewtoolkit.notifier.modules.*"
        )
    }
)

@KotlinScript(
    displayName = "ORT Notifications Script",
    fileExtension = "notifications.kts",
    compilationConfiguration = NotificationScriptCompilationConfiguration::class
)
open class NotificationsScriptTemplate(
    val ortResult: OrtResult
)
