/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.ivy

import java.io.File

import org.ossreviewtoolkit.utils.common.CommandLineTool

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

/**
 * Command line interface for Apache Ivy.
 */
internal object IvyCommand : CommandLineTool {
    override fun command(workingDir: File?) = "ivy"

    override fun getVersionArguments() = "-version"

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=2.4.0")

    override fun transformVersion(output: String): String {
        // Ivy version output format: "Apache Ivy 2.5.0 - 20191020104435"
        return output.substringAfter("Apache Ivy ").substringBefore(" -").trim()
    }
}
