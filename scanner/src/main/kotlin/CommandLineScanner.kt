/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner

import java.io.File
import java.io.IOException

import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.ort.log

/**
 * A [PathScanner] that is executed as a [CommandLineTool] on the local machine.
 */
abstract class CommandLineScanner(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : PathScanner(name, scannerConfig, downloaderConfig), CommandLineTool {
    /**
     * The directory the scanner was bootstrapped to, if so.
     */
    private val scannerDir by lazy {
        val scannerExe = command()

        Os.getPathFromEnvironment(scannerExe)?.parentFile?.also {
            val actualVersion = getVersion(it)
            if (actualVersion != expectedVersion) {
                log.warn {
                    "ORT is currently tested with $name version $expectedVersion, but you are using version " +
                            "$actualVersion. This could lead to problems with parsing the $name output."
                }
            }
        } ?: run {
            if (scannerExe.isNotEmpty()) {
                log.info {
                    "Bootstrapping scanner '$scannerName' as expected version $expectedVersion was not found in PATH."
                }

                val (bootstrapDirectory, duration) = measureTimedValue {
                    bootstrap().also {
                        val actualVersion = getVersion(it)
                        if (actualVersion != expectedVersion) {
                            throw IOException(
                                "Bootstrapped scanner version $actualVersion does not match expected version " +
                                        "$expectedVersion."
                            )
                        }
                    }
                }

                log.info { "Bootstrapped scanner '$scannerName' version $expectedVersion in $duration." }

                bootstrapDirectory
            } else {
                log.info { "Skipping to bootstrap scanner '$scannerName' as it has no executable." }

                File("")
            }
        }
    }

    /**
     * The expected version of the scanner. This is also the version that would get bootstrapped.
     */
    protected abstract val expectedVersion: String

    /**
     * The full path to the scanner executable.
     */
    protected val scannerPath by lazy { scannerDir.resolve(command()) }

    /**
     * The actual version of the scanner, or an empty string in case of failure.
     */
    override val version by lazy { getVersion(scannerDir) }

    /**
     * Bootstrap the scanner to be ready for use, like downloading and / or configuring it.
     *
     * @return The directory the scanner is installed in.
     */
    protected open fun bootstrap(): File = throw NotImplementedError()
}
