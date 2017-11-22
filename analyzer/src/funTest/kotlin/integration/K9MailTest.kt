package com.here.ort.analyzer.integration

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
            //TODO: add a revision to have stable analyzer results
            vcsRevision = "")

    override val expectedResultsDir = ""
}
