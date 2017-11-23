package com.here.ort.analyzer.integration

import com.here.ort.model.Package

class GradleLoggingSubprojectTest : BaseGradleSpec() {
    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "org.gradle",
            name = "gradle_logging",
            version = "",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            downloadUrl = "",
            vcsProvider = "Git",
            vcsUrl = "git@github.com:gradle/gradle.git",
            hashAlgorithm = "",
            hash = "",
            vcsRevision = "",
            vcsPath = "subprojects/logging/"
            )

    override val expectedResultsDir = "src/funTest/assets/projects/synthetic/gradle-expected-results/gradle/"
}
