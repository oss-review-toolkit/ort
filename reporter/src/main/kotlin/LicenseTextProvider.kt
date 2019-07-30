package com.here.ort.reporter.reporters

import com.here.ort.spdx.getLicenseText
import java.io.File

class LicenseTextProvider(private val customLicenseTextsDir: File?) {

    fun getLicenseText(licenseId: String): String? {
        return getLicenseText(
            id = licenseId,
            handleExceptions = true,
            customLicenseTextsDir = customLicenseTextsDir
        )
    }
}
