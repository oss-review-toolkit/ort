/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import java.io.File

import org.ossreviewtoolkit.utils.common.CommandLineTool

import org.semver4j.Semver

internal object NodeCommand : CommandLineTool {
    // As of Node 23.8.0, there is support to use trusted CA certificates present in the system store.
    val hasUseSystemCaOption by lazy { Semver(getVersion()).isGreaterThanOrEqualTo("23.8.0") }

    override fun command(workingDir: File?) = "node"

    override fun transformVersion(output: String) = output.removePrefix("v").trimEnd()
}
