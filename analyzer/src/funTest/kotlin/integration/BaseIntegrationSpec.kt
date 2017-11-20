package com.here.ort.analyzer.integration

import com.here.ort.analyzer.Expensive
import java.io.File

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import com.here.ort.downloader.Main as DownloaderMain
import com.here.ort.analyzer.Main as AnalyzerMain
import com.here.ort.analyzer.managers.Gradle
import com.here.ort.model.Package

abstract class BaseIntegrationSpec : StringSpec() {

    abstract val pkg: Package
    private val outputDir = createTempDir()

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        spec();
        outputDir.deleteRecursively()
    }

    init {
        "analyzer produces ABCD files for all pkg build.gradle files" {
            //FIXME:  Analyzer crashes on JAVA 9 with project below Gradle 4.3 (Gradle issue: https://github.com/gradle/gradle/issues/3317)
            val downloadedDir = DownloaderMain.download(pkg, outputDir)
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

            val expectedResult = sourceGradleProjectFiles.map {
                val originalPath = it.absolutePath.substringBeforeLast(File.separator)
                val toBeReplaced = downloadedDir.absolutePath
                val replacement = analyzerResultsDir.absolutePath
                val abcdFileDir = originalPath.replace(oldValue = toBeReplaced, newValue = replacement, ignoreCase = true)
                abcdFileDir + File.separator + "build-gradle-dependencies.yml"
            }.toSet()
            val generatedResultFiles = analyzerResultsDir.walkTopDown().asIterable().filter { it.extension == "yml" }.map{it.absolutePath}.toSet()
            generatedResultFiles shouldBe expectedResult

        }.config(tags = setOf(Expensive))

        "analyzer results match expected ABCD files" {
            //TODO: add expected ABCD files to assets and test if all produced analyzer outputs match corresponding expected files

        }.config(tags = setOf(Expensive))
    }



}
