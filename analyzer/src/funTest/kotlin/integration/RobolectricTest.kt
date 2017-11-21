package com.here.ort.analyzer.integration

import com.here.ort.analyzer.Expensive
import com.here.ort.model.Package

import io.kotlintest.matchers.shouldBe

import java.io.File


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

    init {
        "analyzer results for robolectric project build.gradle match expected" {
            val projectDir = File("src/funTest/assets/projects/synthetic/integration/robolectric-expected-results/")
            val expectedResult = File(projectDir, "build-gradle-dependencies.yml")
                    .readText()
                    .replaceFirst("vcs_revision: \"\\w\"", "vcs_revision: \"\"")
            val analyzerResultsDir = File(outputDir, "analyzer_results");
            val analyzerResultsForProjectFileContents = File(analyzerResultsDir, "build-gradle-dependencies.yml")
                    .readText()
                    .replaceFirst("vcs_revision:\\s*\"[^#\"]+\"".toRegex(), "vcs_revision: \"\"")
            analyzerResultsForProjectFileContents shouldBe expectedResult
        }.config(tags = setOf(Expensive))
    }
}
