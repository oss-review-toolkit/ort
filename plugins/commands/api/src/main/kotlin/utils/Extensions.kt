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

package org.ossreviewtoolkit.plugins.commands.api.utils

import com.github.ajalt.clikt.core.GroupableOption
import com.github.ajalt.mordant.terminal.Terminal

import java.io.File
import java.lang.invoke.MethodHandles

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.utils.common.formatSizeInMib

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

fun <T : GroupableOption> T.group(name: String): T = apply { groupName = name }

fun <T : GroupableOption> T.inputGroup(): T = group(OPTION_GROUP_INPUT)

fun <T : GroupableOption> T.outputGroup(): T = group(OPTION_GROUP_OUTPUT)

fun <T : GroupableOption> T.configurationGroup(): T = group(OPTION_GROUP_CONFIGURATION)

/**
 * Read [ortFile] into an [OrtResult] and return it.
 */
fun readOrtResult(ortFile: File): OrtResult {
    logger.debug { "Input ORT result file has SHA-1 hash ${HashAlgorithm.SHA1.calculate(ortFile)}." }

    val (ortResult, duration) = measureTimedValue { ortFile.readValue<OrtResult>() }

    logger.info { "Read ORT result from '${ortFile.name}' (${ortFile.formatSizeInMib}) in $duration." }

    return ortResult
}

/**
 * Write the [ortResult] to all [outputFiles].
 */
fun writeOrtResult(ortResult: OrtResult, outputFiles: Collection<File>, terminal: Terminal) {
    outputFiles.forEach { file ->
        val resultName = file.name.substringBefore('-')
        val duration = measureTime { file.writeValue(ortResult) }

        terminal.println("Wrote $resultName result to '$file' (${file.formatSizeInMib}) in $duration.")

        logger.debug { "Output ORT result file has SHA-1 hash ${HashAlgorithm.SHA1.calculate(file)}." }
    }
}
