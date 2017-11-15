package com.here.ort.analyzer.integration

import com.here.ort.model.Package

class K9MailTest : BaseGradleSpec() {

    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "com.fsck.k9",
            name = "k9mail",
            version = "",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            downloadUrl = "",
            vcsPath = "",
            vcsProvider = "Git",
            vcsUrl = "git@github.com:k9mail/k-9.git",
            hashAlgorithm = "",
            hash = "",
            vcsRevision = "934bbbe88299b1d468315917924123e4a8b89883")

    override val expectedResultsDir = ""
}
