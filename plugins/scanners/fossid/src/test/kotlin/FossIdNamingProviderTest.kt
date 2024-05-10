package org.ossreviewtoolkit.plugins.scanners.fossid

import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.clock.TestClock
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.mockk.every
import io.mockk.mockkStatic
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class FossIdNamingProviderTest : WordSpec({

    "createScanCode" should {
        val namingProvider = FossIdNamingProvider(null, null, HashMap());

        val clock = TestClock(Instant.ofEpochMilli(1711965600000), ZoneOffset.UTC)
        val mockedDateTime = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("UTC"))
        val expectedTimestamp = "20240401_100000";

        "create code without branch name, when it's empty" {
            mockkStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns mockedDateTime

                namingProvider.createScanCode(
                    "example-project-name", null, ""
                ) shouldBeEqual "example-project-name_$expectedTimestamp"
            }
        }

        "create code with branch name" {
            mockkStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns mockedDateTime

                namingProvider.createScanCode(
                    "example-project-name", null, "CODE-2233_Red-dots-added-to-layout"
                ) shouldBeEqual "example-project-name_" + expectedTimestamp + "_CODE-2233_Red-dots-added-to-layout"
            }
        }

        "create code with branch name and delta tag" {
            mockkStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns mockedDateTime

                namingProvider.createScanCode(
                    "example-project-name", FossId.DeltaTag.DELTA, "CODE-2233_Red-dots-added-to-layout"
                ) shouldBeEqual "example-project-name_" + expectedTimestamp +
                        "_delta_CODE-2233_Red-dots-added-to-layout"
            }
        }

        "remove all non-standard signs from branch name when creating code" {
            mockkStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns mockedDateTime

                namingProvider.createScanCode(
                    "example-project-name", null, "feature/CODE-12%%$@@&^_SOME_*&^#!*text!!"
                ) shouldBeEqual "example-project-name_" +
                        expectedTimestamp + "_feature_CODE-12________SOME_______text__"
            }
        }

        "truncate very long scan id to fit maximum length accepted by FossID (255 chars)" {
            val veryLongBranchName =
                "origin/feature/CODE-123321_some_more_detailed_description_of_what_that_feature_doing_just_to_make_" +
                        "it_as_descriptive_as_it's_possible_otherwise_this_test_is_pointless_so_lets_add_some_more_" +
                        "characters_to_test_it_properly_to_avoid_any_mistake_so_lets_add_some_even_more_to_it_yolo"

            mockkStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns mockedDateTime

                namingProvider.createScanCode(
                    "example-project-name", FossId.DeltaTag.DELTA, veryLongBranchName
                ).length shouldBeLessThanOrEqual 255
            }
        }
    }
})
