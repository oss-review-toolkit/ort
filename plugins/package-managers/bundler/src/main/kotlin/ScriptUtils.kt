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
import org.jruby.util.cli.Options
import org.jruby.util.collections.ClassValue

import org.ossreviewtoolkit.utils.common.Os

internal val SCRIPT_ENVIRONMENT = mapOf("BUNDLE_PATH" to "${Os.userHomeDirectory}/.bundle")

/**
 * Run the given Ruby [script] in the given [workingDir] and with the given [environment] (which gets merged with the
 * default environment). If no [pathType] is given, the [script] is assumed to contain the code directly. Otherwise, the
 * meaning of the [script] parameter is determined by the [pathType]. As the Ruby runtime is terminated afterwards, the
 * [script] should only return primitive values like numbers or strings.
 */
internal fun runScript(
    script: String,
    pathType: PathType? = null,
    workingDir: File? = null,
    environment: Map<String, String> = emptyMap()
): Any? {
    // By default, JRuby stores Java integration data in a way that keeps every runtime that was ever created reachable,
    // which leaks memory as each script runs in its own runtime. Force a storage type that is owned by the runtime.
    Options.JI_CLASS_VALUES.force(ClassValue.Type.HARD_MAP.name)

    val container = ScriptingContainer(LocalContextScope.THREADSAFE)

    return try {
        if (workingDir != null) container.currentDirectory = workingDir.path

        container.environment.putAll(environment)

        if (pathType != null) {
            container.runScriptlet(pathType, script)
        } else {
            container.runScriptlet(script)
        }
    } finally {
        // Terminate the runtime to also stop any threads it started, as these would otherwise keep the runtime alive.
        container.terminate()
    }
}
