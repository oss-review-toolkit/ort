import com.here.ort.reporter.CopyrightStatementsProcessor
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec
import java.io.File

class CopyrightStatementsProcessorTest : WordSpec() {
    val processor = CopyrightStatementsProcessor()

    init {
        "process" should {
            "produce expeced output" {
                val input = File("src/test/assets/copyright-statements.txt").readLines()

                val result = processor.process(input).toDebugString()

                val expectedResult = File("src/test/assets/copyright-statements-expeceted-output.yml").readText()
                result shouldBe expectedResult
            }
        }
    }
}
