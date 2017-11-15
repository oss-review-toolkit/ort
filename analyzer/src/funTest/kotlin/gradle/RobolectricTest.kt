package com.here.ort.analyzer.integration

import com.here.ort.model.Package

class RobolectricTest : BaseGradleSpec() {

    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "org.robolectric",
            name = "robolectric",
            version = "3.3.2",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            downloadUrl = "",
            hash = "",
            hashAlgorithm = "",
            vcsProvider = "Git",
            vcsUrl = "https://github.com/robolectric/robolectric.git",
            vcsRevision = "757dfd56499a415376ea04bfa520b317bf2e3b58",
            vcsPath = ""
    )


    override val expectedResultsDir = "src/funTest/assets/projects/synthetic/gradle-expected-results/robolectric/"

}
