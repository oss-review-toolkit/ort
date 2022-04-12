/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.utils.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.string.endWith
import io.kotest.matchers.string.startWith

import java.io.File

import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.EMPTY_PACKAGE_VERIFICATION_CODE
import org.ossreviewtoolkit.utils.test.createTestTempDir

class UtilsTest : WordSpec() {
    private lateinit var tempDir: File

    override suspend fun beforeEach(testCase: TestCase) {
        tempDir = createTestTempDir()
    }

    private fun setupTempFile(filename: String, content: String) =
        tempDir.resolve(filename).apply { writeText(content) }

    init {
        "calculatePackageVerificationCode" should {
            "return the SHA1 for an empty string on no input" {
                calculatePackageVerificationCode(emptySequence<String>()) shouldBe EMPTY_PACKAGE_VERIFICATION_CODE
            }

            "work for given SHA1s and excludes" {
                val sha1sums = sequenceOf(
                    "0811bcab4e7a186f4d0d08d44cc5f06d721e7f6d",
                    "f7a535db519cf832c1119fecdf1ea0514f583886",
                    "0db752599b67b64dd1bdeff77ed9f5aa5437d027",
                    "9706f99c85a781c016a22fd23313e55257e7b3e8",
                    "1d96c52b533a38492ce290bc6831f8702f690e8e",
                    "e77075d2fb2cdeb4406538d9b33d00fd823a527a",
                    "b2f60873fd2c0feaf21eaccaf7ba052ceb12b146",
                    "1c2d4960f5d156e444f9819cec0c44c09f98970f",
                    "ca5fa85664a953084ca9a1de1e393698257495c0",
                    "0d172bac2b6712ecdc7b1e639c3cf8104f2a6a2a",
                    "14b6a02753d05f72ac57144f1ea0da52d97a0ce3",
                    "25306873d3e2434aade745e8a1a6914c215165f6",
                    "afd495c14035961a55d725ba0127e166349f28b9",
                    "1b8f69fa87f1abedd02f6c4766f07f0ceeea7a02",
                    "a4410f034f97b67eccbb8c9596d58c97ad3de988",
                    "a1663801985f4361395831ae17b3256e38810dc2",
                    "2a06f5906e5afb1b283b6f4fd6a21e7906cdde4f",
                    "1a409fc2dcd3dd10549c47793a80c42c3a06c9f0",
                    "2cc787ebd4d29f2e24646f76f9c525336949783e",
                    "3ea2f82d7ce6f638e9466365a328a201f2caa579",
                    "9257afd2d46c3a189ec0d40a45722701d47e9ca5",
                    "4ff8a82b52e1e1c5f8bf0abb25c20859d3f06c62",
                    "0e8faebf9505c9b1d5462adcf34e01e83d110cc8",
                    "824cadd41d399f98d17ae281737c6816846ac75d",
                    "f54dd0df3ab62f1d5687d98076dffdbf690840f6",
                    "91742d83b0feadb4595afeb4e7f4bab2e85f4a98",
                    "64dd561478479f12deda240ae9fe569952328bff",
                    "310fc965173381a02fbe83a889f7c858c4499862"
                )

                val excludes = sequenceOf("./package.spdx")

                calculatePackageVerificationCode(sha1sums) shouldBe "1a74d8321c452522ec516a46893e6a42f36b5953"
                calculatePackageVerificationCode(sha1sums, excludes) shouldBe
                        "1a74d8321c452522ec516a46893e6a42f36b5953 (excludes: ./package.spdx)"
            }

            "work for given files and excludes" {
                val files = sequenceOf(
                    setupTempFile("fileA", "Hello"),
                    setupTempFile("fileB", "World")
                )

                val excludes = sequenceOf("./package.spdx")

                calculatePackageVerificationCode(files) shouldBe "378d5a37b5b10b90535e32a190014d2a8d25354a"
                calculatePackageVerificationCode(files, excludes) shouldBe
                        "378d5a37b5b10b90535e32a190014d2a8d25354a (excludes: ./package.spdx)"
            }

            "work for a given file" {
                val file = setupTempFile("file", "file")

                calculatePackageVerificationCode(file) shouldBe "81e250a78cc6386afc25fa57ad6eaee31394019b"
            }

            "work for a given directory" {
                setupTempFile("fileA", "fileA")
                setupTempFile("fileB", "fileB")
                setupTempFile("package.spdx", "content")
                tempDir.resolve("dir").mkdir()
                setupTempFile("dir/fileC", "fileC")
                setupTempFile("dir/package.spdx", "content")

                calculatePackageVerificationCode(tempDir) shouldBe
                        "15d3fa138d9302ec9a1584180b5eba0d342b60fa (excludes: ./package.spdx, ./dir/package.spdx)"
            }

            "exclude VCS directories" {
                setupTempFile("file", "file")
                VCS_DIRECTORIES.forEach {
                    tempDir.resolve(it).mkdir()
                    setupTempFile("$it/dummy", "dummy")
                }

                calculatePackageVerificationCode(tempDir) shouldBe
                        "81e250a78cc6386afc25fa57ad6eaee31394019b"
            }
        }

        "getLicenseText" should {
            "return the full license text for a valid SPDX license id" {
                val text = getLicenseText("Apache-2.0")?.trim()

                text should startWith("Apache License")
                text should endWith("limitations under the License.")
            }

            "return the full license text for a valid SPDX license id with the '+' operator" {
                val text = getLicenseText("Apache-2.0+")?.trim()

                text should startWith("Apache License")
                text should endWith("limitations under the License.")
            }

            "return null for an invalid SPDX license id" {
                getLicenseText("FooBar-1.0") should beNull()
            }

            "return the exception text for an SPDX exception id if handling exceptions is enabled" {
                val text = getLicenseText("Autoconf-exception-3.0", true)?.trim()

                text should startWith("AUTOCONF CONFIGURE SCRIPT EXCEPTION")
                text should endWith("the copyleft requirements of the license of Autoconf.")
            }

            "return null for an SPDX exception id if handling exceptions is disabled" {
                getLicenseText("Autoconf-exception-3.0", false) should beNull()
            }

            "return a non-blank string for all SPDX ids" {
                enumValues<SpdxLicense>().forEach {
                    getLicenseText(it.id) shouldNot beBlank()
                }
            }

            "return null for an unknown SPDX LicenseRef" {
                getLicenseText("LicenseRef-foo-bar") should beNull()
            }

            "return the full license text for a LicenseRef-ort license" {
                val text = getLicenseText("LicenseRef-ort-oracle-futc")?.trim()

                text should startWith("Oracle Free Use Terms and Conditions")
                text should endWith("Last updated: 9 October 2018")
            }
        }

        "getLicenseText provided a custom dir" should {
            "return the custom license text for a license ID not known by ort but in custom dir" {
                val id = "LicenseRef-ort-abc"
                val text = "a\nb\nc\n"

                setupTempFile(id, text)

                getLicenseText(id, handleExceptions = true, listOf(tempDir)) shouldBe text
            }

            "return null if license text is not known by ort and also not in custom dir" {
                setupTempFile("LicenseRef-ort-abc", "abc")

                getLicenseText("LicenseRef-not-present", handleExceptions = true, listOf(tempDir)) should beNull()
            }
        }
    }
}
