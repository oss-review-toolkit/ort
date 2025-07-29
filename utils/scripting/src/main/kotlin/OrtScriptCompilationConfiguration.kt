/*
 * Copyright (C) 2010 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.isStandalone
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache

import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

class OrtScriptCompilationConfiguration : ScriptCompilationConfiguration({
    compilerOptions("-jvm-target", Environment.JAVA_VERSION.substringBefore('.'))

    defaultImports(
        "org.apache.logging.log4j.kotlin.logger",
        "org.ossreviewtoolkit.utils.common.*",
        "org.ossreviewtoolkit.utils.ort.*",
        "java.util.*"
    )

    hostConfiguration(
        ScriptingHostConfiguration {
            jvm {
                val scriptCacheDir = ortDataDirectory.resolve("cache/scripts").safeMkdirs()

                compilationCache(
                    CompiledScriptJarsCache { script, configuration ->
                        val cacheKey = generateUniqueName(script, configuration)
                        scriptCacheDir / "$cacheKey.jar"
                    }
                )
            }
        }
    )

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

    isStandalone(false)

    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
})

private val digest = MessageDigest.getInstance("SHA-1")

private fun generateUniqueName(script: SourceCode, configuration: ScriptCompilationConfiguration): String {
    digest.reset()
    digest.update(script.text.toByteArray())

    configuration.notTransientData.entries
        .sortedBy { it.key.name }
        .forEach {
            digest.update(it.key.name.toByteArray())
            digest.update(it.value.toString().toByteArray())
        }

    return digest.digest().toHexString()
}
