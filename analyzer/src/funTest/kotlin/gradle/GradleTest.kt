package com.here.ort.analyzer.integration

import com.here.ort.model.Package
import com.here.ort.downloader.Main as DownloaderMain
import com.here.ort.analyzer.Main as AnalyzerMain

/**
 * Cannot fetch on gradle on  windows:
 */
class GradleTest : BaseGradleSpec() {
    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "org.gradle",
            name = "gradle_logging",
            version = "",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = null,
            downloadUrl = null,
            vcsProvider = "Git",
            vcsUrl = "git@github.com:gradle/gradle.git",
            hashAlgorithm = null,
            hash = "",
            vcsRevision = "",
            vcsPath = "subprojects/logging/"
            )

    override val expectedResultsDir = "src/funTest/assets/projects/synthetic/gradle-expected-results/gradle/"
}
