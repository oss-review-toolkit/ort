package org.ossreviewtoolkit.plugins.packagemanagers.gradle

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.should
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class GradleVersionsTest : FunSpec({
    withData(
        null, // 7.x, from the project's gradle/gradle-wrapper.properties
        "8.14.3",
        "9.0.0",
        "9.3.1"
    ) { gradleVersion ->
        val definitionFile = getAssetFile("projects/synthetic/gradle-library/app/build.gradle")
        val expectedResultFile = getAssetFile("projects/synthetic/gradle-library-expected-output-app.yml")

        val result = GradleFactory.create(javaVersion = "17", gradleVersion = gradleVersion)
            .resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)

    }
})
