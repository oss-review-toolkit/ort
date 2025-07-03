/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.licensefactproviders.scancode

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProviderFactory
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.realFile

private val FALLBACK_DIR = File("/opt/scancode-license-data")

/** The configuration for the ScanCode license fact provider. */
data class ScanCodeLicenseFactProviderConfig(
    /**
     * The directory that contains the ScanCode license texts. If not set, the provider will try to locate the ScanCode
     * license text directory using a heuristic based on the path of the ScanCode binary.
     */
    val scanCodeLicenseTextDir: String?
)

@OrtPlugin(
    id = "ScanCode",
    displayName = "ScanCode License Fact Provider",
    description = "A license fact provider that reads license information from a local ScanCode installation.",
    factory = LicenseFactProviderFactory::class
)
class ScanCodeLicenseFactProvider(
    override val descriptor: PluginDescriptor = ScanCodeLicenseFactProviderFactory.descriptor,
    private val config: ScanCodeLicenseFactProviderConfig
) : LicenseFactProvider {
    /**
     * The directory that contains the ScanCode license texts. This is located using a heuristic based on the path of
     * the ScanCode binary.
     */
    private val scanCodeLicenseTextDir: File by lazy {
        if (config.scanCodeLicenseTextDir != null) {
            return@lazy File(config.scanCodeLicenseTextDir).also {
                require(it.isDirectory) {
                    "Configured ScanCode license text directory '${config.scanCodeLicenseTextDir}' does not exist or " +
                        "is not a directory."
                }

                logger.debug { "Using configured ScanCode license text directory: ${it.absolutePath}" }
            }
        }

        logger.debug { "Trying to locate the ScanCode license text directory..." }

        val scanCodeExeDir = Os.getPathFromEnvironment("scancode")?.realFile?.parentFile

        if (scanCodeExeDir == null) {
            logger.debug { "Could not locate the ScanCode executable directory." }
        } else {
            logger.debug { "Located ScanCode executable directory: ${scanCodeExeDir.absolutePath}" }
        }

        val pythonBinDir = listOf("bin", "Scripts")
        val scanCodeBaseDir = scanCodeExeDir?.takeUnless { it.name in pythonBinDir } ?: scanCodeExeDir?.parentFile

        if (scanCodeBaseDir == null) {
            logger.debug { "Could not locate the ScanCode base directory." }
        } else {
            logger.debug { "Located ScanCode base directory: ${scanCodeBaseDir.absolutePath}" }
        }

        val licenseDir = scanCodeBaseDir?.walk()?.find { it.isDirectory && it.endsWith("licensedcode/data/licenses") }

        if (licenseDir == null) {
            logger.debug { "Could not locate the ScanCode license text directory." }
        } else {
            logger.debug { "Located ScanCode license text directory: ${licenseDir.absolutePath}" }
            return@lazy licenseDir
        }

        FALLBACK_DIR.takeIf { it.isDirectory }.also {
            if (it == null) {
                logger.debug { "Could not locate fallback directory: $FALLBACK_DIR" }
            } else {
                logger.debug { "Located fallback ScanCode license text directory: ${it.absolutePath}" }
                return@lazy it
            }
        }

        error("Could not locate the ScanCode license text directory.")
    }

    private fun getLicenseTextFile(licenseId: String): File? {
        val filename = if (licenseId == "LicenseRef-scancode-x11-xconsortium-veillard") {
            // Work around for https://github.com/aboutcode-org/scancode-toolkit/issues/2813 which affects ScanCode
            // versions below 31.0.0.
            "x11-xconsortium_veillard.LICENSE"
        } else {
            "${licenseId.removePrefix("LicenseRef-scancode-").lowercase()}.LICENSE"
        }

        return scanCodeLicenseTextDir.resolve(filename).takeIf { it.isFile }
    }

    override fun getLicenseText(licenseId: String): String? =
        getLicenseTextFile(licenseId)?.readText()?.removeYamlFrontMatter()

    override fun hasLicenseText(licenseId: String): Boolean = getLicenseTextFile(licenseId) != null
}

internal fun String.removeYamlFrontMatter(): String {
    val lines = lines()

    // Remove any YAML front matter enclosed by "---" from ScanCode license files.
    val licenseLines = lines.takeUnless { it.first() == "---" }
        ?: lines.drop(1).dropWhile { it != "---" }.drop(1)

    return licenseLines.dropWhile { it.isEmpty() }.joinToString("\n").trimEnd()
}
