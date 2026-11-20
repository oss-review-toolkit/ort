/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import java.io.File

import org.jruby.embed.LocalContextScope
import org.jruby.embed.PathType
import org.jruby.embed.ScriptingContainer

import org.ossreviewtoolkit.utils.common.Os

internal val SCRIPT_ENVIRONMENT = mapOf("BUNDLE_PATH" to "${Os.userHomeDirectory}/.bundle")

/**
 * Run the given Ruby [script] in the given [workingDir] and with the given [environment] (which gets merged with the
 * default environment). If no [pathType] is given, the [script] is assumed to contain the code directly. Otherwise, the
 * meaning of the [script] parameter is determined by the [pathType].
 */
internal fun runScript(
    script: String,
    pathType: PathType? = null,
    workingDir: File? = null,
    environment: Map<String, String> = emptyMap()
): Any? {
    val output = with(ScriptingContainer(LocalContextScope.THREADSAFE)) {
        if (workingDir != null) currentDirectory = workingDir.path

        this.environment.putAll(environment)

        if (pathType != null) {
            runScriptlet(pathType, script)
        } else {
            runScriptlet(script)
        }
    }

    return output
}
