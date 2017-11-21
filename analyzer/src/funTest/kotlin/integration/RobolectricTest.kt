package com.here.ort.analyzer.integration

import com.here.ort.model.Package

import io.kotlintest.matchers.shouldBe
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table

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
        "analyzer results for robolectric project build.gradle files match expected" {
            val analyzerResultsDir = File(outputDir, "analyzer_results/")
            val testRows = analyzerResultsDir.walkTopDown().asIterable().filter { file: File ->
                file.extension == "yml"
            }.map {
                val expectedResultPath = "src/funTest/assets/projects/synthetic/integration/robolectric-expected-results/" + it.path.substringBeforeLast(File.separator).substringAfterLast("analyzer_results")
                row(it, File(expectedResultPath, "build-gradle-dependencies.yml"))
            }
            val gradleBuildPathsTable2 = table(
                    headers("analyzerResultPath", "expectedResultPath"),
                    *testRows.toTypedArray())

            forAll(gradleBuildPathsTable2) { analyzerOutputFile, expectedResultFile ->
                val analyzerResults =analyzerOutputFile.readText().replaceFirst("vcs_revision:\\s*\"[^#\"]+\"".toRegex(), "vcs_revision: \"\"")
                val expectedResults = expectedResultFile.readText().replaceFirst("vcs_revision:\\s*\"[^#\"]+\"".toRegex(), "vcs_revision: \"\"")
                analyzerResults shouldBe expectedResults

            }
        }
    }
}
