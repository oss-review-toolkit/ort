import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File
import java.nio.file.Paths

class JgnashTest : StringSpec() {

    fun readResource(path: String): List<String> {
        return File(javaClass.getResource(path).toURI()).readLines()
    }

    init {
        "computed dependencies are correct" {
            val expectedDependencies = readResource("/projects/external/jgnash-expected-maven-dependencies.txt")
            val resolvedDependencies = Maven.resolveDependencies(listOf(Paths.get("projects/external/jgnash/pom.xml")))

            resolvedDependencies shouldBe expectedDependencies
        }
    }
}
