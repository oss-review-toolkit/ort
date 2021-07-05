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

package org.ossreviewtoolkit.notifier

import java.time.Instant

import org.ossreviewtoolkit.model.NotifierRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.NotifierConfiguration
import org.ossreviewtoolkit.notifier.modules.EmailNotifier
import org.ossreviewtoolkit.utils.ScriptRunner

class Notifier(ortResult: OrtResult = OrtResult.EMPTY, config: NotifierConfiguration = NotifierConfiguration()) :
    ScriptRunner() {
    override val preface = """
            import org.ossreviewtoolkit.model.*
            import org.ossreviewtoolkit.model.config.*
            import org.ossreviewtoolkit.model.licenses.*
            import org.ossreviewtoolkit.model.utils.*
            import org.ossreviewtoolkit.notifier.modules.*
            import org.ossreviewtoolkit.utils.*

            import java.util.*

        """.trimIndent()

    init {
        engine.put("ortResult", ortResult)

        config.mail?.let { engine.put("emailClient", EmailNotifier(it)) }
    }

    override fun run(script: String): NotifierRun {
        val startTime = Instant.now()

        super.run(script)

        val endTime = Instant.now()

        return NotifierRun(startTime, endTime)
    }
}
