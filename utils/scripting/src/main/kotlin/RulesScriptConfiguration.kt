/*
 * Copyright (C) 2021 Bosch.IO GmbH
 * Copyright (C) 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors
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

import java.security.MessageDigest

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.toHexString
import org.ossreviewtoolkit.utils.core.ortDataDirectory

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
        "org.ossreviewtoolkit.model.*",
        "org.ossreviewtoolkit.model.config.*",
        "org.ossreviewtoolkit.model.licenses.*",
        "org.ossreviewtoolkit.model.utils.*",
        "org.ossreviewtoolkit.utils.common.*",
        "org.ossreviewtoolkit.utils.core.*",
        "java.util.*"
    )

    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }

    hostConfiguration(ScriptingHostConfiguration {
        jvm {
            val scriptCacheDir = ortDataDirectory.resolve("cache/scripts").apply { safeMkdirs() }

            compilationCache(
                CompiledScriptJarsCache { script, scriptCompilationConfiguration ->
                    val cacheKey = generateUniqueName(script, scriptCompilationConfiguration)
                    scriptCacheDir.resolve("$cacheKey.jar")
                }
            )
        }
    })
})

// Use MD5 for speed.
private val digest = MessageDigest.getInstance("MD5")

private fun generateUniqueName(
    script: SourceCode,
    scriptCompilationConfiguration: ScriptCompilationConfiguration
): String {
    digest.reset()
    digest.update(script.text.toByteArray())

    scriptCompilationConfiguration.notTransientData.entries
        .sortedBy { it.key.name }
        .forEach {
            digest.update(it.key.name.toByteArray())
            digest.update(it.value.toString().toByteArray())
        }

    return digest.digest().toHexString()
}
