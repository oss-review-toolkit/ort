package com.here.ort.analyzer

import com.here.ort.model.Package

class K9MailTest : BaseIntegrationSpec() {

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
            vcsRevision = "")

}
