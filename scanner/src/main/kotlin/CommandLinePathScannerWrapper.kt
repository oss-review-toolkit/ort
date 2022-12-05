/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.utils.common.CommandLineTool

/**
 * A [PathScannerWrapper] that is executed as a [CommandLineTool] on the local machine.
 */
abstract class CommandLinePathScannerWrapper(name: String) : PathScannerWrapper, CommandLineTool {
    companion object : Logging

    /**
     * The configuration used by the scanner, should contain command line options that influence the scan result.
     */
    abstract val configuration: String

    override val details by lazy { ScannerDetails(name, getVersion(), configuration) }

    /**
     * Return the invariant relative path of the [scanned file][scannedFilename] with respect to the
     * [scanned path][scanPath].
     */
    protected fun relativizePath(scanPath: File, scannedFilename: File): String {
        val relativePathToScannedFile = if (scannedFilename.isAbsolute) {
            if (scanPath.isFile) {
                scannedFilename.relativeTo(scanPath.parentFile)
            } else {
                scannedFilename.relativeTo(scanPath)
            }
        } else {
            scannedFilename
        }

        return relativePathToScannedFile.invariantSeparatorsPath
    }
}
