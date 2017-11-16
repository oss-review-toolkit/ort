package com.here.ort.analyzer


import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

import com.here.ort.analyzer.managers.Gradle
import com.here.ort.model.Package
import io.kotlintest.Spec
import com.here.ort.downloader.Main as DownloaderMain
import com.here.ort.analyzer.Main as AnalyzerMain

class RobolectricTest : StringSpec() {

    private val outputDir = createTempDir()

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        spec();
        outputDir.deleteRecursively()
    }

    val robolectricPackage = Package(
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
            vcsRevision = "")

    init {
        "analyzer produces ABCD files for all robolectric build.gradle files" {
            val downloadedDir = DownloaderMain.download(robolectricPackage, outputDir)
            val analyzerResultsDir = File(outputDir, "analyzer_results");
            AnalyzerMain.main(arrayOf(
                    "-i", downloadedDir.absolutePath,
                    "-o", analyzerResultsDir.absolutePath
            ))

            val sourceGradleProjectFiles = downloadedDir.walkTopDown().asIterable().filter { file: File ->
                Gradle.matchersForDefinitionFiles.any() { glob ->
                    glob.matches(file.toPath())
                }
            }

            analyzerResultsDir.walkTopDown().asIterable().filter { it.extension == "yml" }.count() shouldBe sourceGradleProjectFiles.size
        }
    }
}
