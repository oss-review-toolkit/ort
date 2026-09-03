/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProviderFactory
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseText
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

private val FALLBACK_DIR = File("/opt/scancode-license-data")

/** The configuration for the ScanCode license fact provider. */
data class ScanCodeLicenseFactProviderConfig(
    /**
     * The directory that contains the ScanCode license data. If not set, the provider will try to locate the ScanCode
     * license data directory using a heuristic based on the path of the ScanCode binary.
     */
    @OrtPluginOption(aliases = ["licenseTextDir", "scanCodeLicenseTextDir"])
    val licenseDataDir: String?
)

@OrtPlugin(
    id = "ScanCode",
    displayName = "ScanCode License Fact Provider",
    summary = "Provide license facts from a local ScanCode installation.",
    factory = LicenseFactProviderFactory::class
)
class ScanCodeLicenseFactProvider(
    override val descriptor: PluginDescriptor = ScanCodeLicenseFactProviderFactory.descriptor,
    private val config: ScanCodeLicenseFactProviderConfig
) : LicenseFactProvider() {
    private val licenseDataDirReader: ScanCodeLicenseDataDirReader? by lazy {
        findScanCodeLicenseDataDir(config)?.let {
            ScanCodeLicenseDataDirReader(it) { scanCodeLicense -> scanCodeLicense.text != null }
        }
    }

    override fun getLicenseText(licenseOrExceptionId: String) =
        licenseDataDirReader?.getLicense(licenseOrExceptionId)?.text?.let { LicenseText(it) }

    override fun hasLicenseText(licenseOrExceptionId: String): Boolean =
        licenseDataDirReader?.hasLicense(licenseOrExceptionId) ?: false
}

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * Return the directory that contains the ScanCode license data. This is located using a heuristic based on the path of
 * the ScanCode binary.
 */
internal fun findScanCodeLicenseDataDir(config: ScanCodeLicenseFactProviderConfig? = null): File? {
    if (config?.licenseDataDir != null) {
        return File(config.licenseDataDir).also {
            require(it.isDirectory) {
                "Configured ScanCode license data directory '${config.licenseDataDir}' does not exist or is not " +
                    "a directory."
            }

            logger.debug { "Using configured ScanCode license data directory: ${it.absolutePath}" }
        }
    }

    findScanCodeInstallationLicenseDataDir()?.also {
        logger.debug { "Located ScanCode license data directory: $it" }
        return it
    } ?: logger.debug { "Could not locate the ScanCode 'licenses' text directory." }

    FALLBACK_DIR.takeIf { it.isDirectory }?.also {
        logger.debug { "Located fallback ScanCode license data directory: $it" }
        return it
    } ?: logger.debug { "Could not locate fallback directory: $FALLBACK_DIR" }

    val exportDir = ortDataDirectory / "scanner" / "scancode-license-data"
    return exportDir.takeIf { it.isDirectory }?.also {
        logger.debug { "Using existing license data from directory: $it" }
    } ?: exportLicenseData(exportDir)?.also {
        logger.debug { "Using exported license data from directory: $it" }
    } ?: run {
        logger.warn { "Could not locate any ScanCode license data directory." }
        null
    }
}

private fun findScanCodeInstallationLicenseDataDir(): File? {
    logger.debug { "Trying to locate the ScanCode license data directory..." }

    val scanCodeExeDir = Os.getPathFromEnvironment("scancode")?.realFile?.parentFile

    if (scanCodeExeDir == null) {
        logger.debug { "Could not locate the ScanCode executable directory." }
    } else {
        logger.debug { "Located ScanCode executable directory: $scanCodeExeDir" }
    }

    val pythonBinDir = listOf("bin", "Scripts")
    val scanCodeBaseDir = scanCodeExeDir?.takeUnless { it.name in pythonBinDir } ?: scanCodeExeDir?.parentFile

    if (scanCodeBaseDir == null) {
        logger.debug { "Could not locate the ScanCode base directory." }
    } else {
        logger.debug { "Located ScanCode base directory: $scanCodeBaseDir" }
    }

    return scanCodeBaseDir?.walk()?.find { it.isDirectory && it.endsWith("licensedcode/data/licenses") }
}

private fun exportLicenseData(targetDir: File): File? {
    val process = ProcessCapture(
        "scancode-license-data", "--path", targetDir.absolutePath,
        environment = mapOf("LC_ALL" to "en_US.UTF-8")
    )

    return targetDir.takeIf { process.isSuccess && it.isDirectory }
}
