package com.here.ort.analyzer.integration

import com.here.ort.model.Package

class RobolectricTest : BaseIntegrationSpec() {

    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "org.robolectric",
            name = "robolectric",
            version = "3.3.2",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            downloadUrl = "",
            vcsPath = "",
            vcsProvider = "Git",
            vcsUrl = "git@github.com:robolectric/robolectric.git",
            hashAlgorithm = "",
            hash = "",
            //TODO: add a revision to have stable analyzer results
            vcsRevision = "")

    override val expectedResultsDir = "src/funTest/assets/projects/synthetic/integration/robolectric-expected-results/"
}
