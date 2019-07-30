package com.here.ort.reporter.reporters

import com.here.ort.spdx.getLicenseText

class LicenseTextProvider {
    fun getLicenseText(licenseId: String): String? {
        return getLicenseText(licenseId, true)
    }
}
