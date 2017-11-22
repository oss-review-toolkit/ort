package com.here.ort.analyzer.integration

import com.here.ort.model.Package
import java.io.File

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

    override val expectedResultsDir = "src/funTest/assets/projects/external/gradle-submodules"

    override val expectedResultsDirsMap = mapOf(
            "${expectedResultsDir}/subprojects/logging/src/integTest/resources/org/gradle/internal/logging/LoggingIntegrationTest/logging/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging/expected-logging-integration-test-dependencies.yml"),
            "${expectedResultsDir}/subprojects/logging/src/integTest/resources/org/gradle/internal/logging/LoggingIntegrationTest/logging/buildSrc/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging/expected-logging-integration-test-build-src-dependencies.yml"),
            "${expectedResultsDir}/subprojects/logging/src/integTest/resources/org/gradle/internal/logging/LoggingIntegrationTest/logging/nestedBuild/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging/expected-logging-integration-test-nested-build-dependencies.yml "),
            "${expectedResultsDir}/subprojects/logging/src/integTest/resources/org/gradle/internal/logging/LoggingIntegrationTest/logging/nestedBuild/buildSrc/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging/expected-logging-integration-test-nested-build-build-src-dependencies.yml"),
            "${expectedResultsDir}/subprojects/logging/src/integTest/resources/org/gradle/internal/logging/LoggingIntegrationTest/logging/project1/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging/expected-logging-integration-test-project1-dependencies.yml"),
            "${expectedResultsDir}/subprojects/logging/src/integTest/resources/org/gradle/internal/logging/LoggingIntegrationTest/logging/project2/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging/expected-logging-integration-test-project2-dependencies.yml"),
            "${expectedResultsDir}/subprojects/logging/src/integTest/resources/org/gradle/internal/logging/LoggingIntegrationTest/multiThreaded/build-gradle-dependencies.yml"
                    to File("src/funTest/assets/projects/external/gradle-submodules/logging/expected-logging-integration-test-multi-threaded-dependencies.yml")
    )
}
