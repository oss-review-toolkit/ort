/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class PackageManagerTest : WordSpec({
    "parseAuthorString()" should {
        "return the name, email, and URL" {
            parseAuthorString("Brandon Alexander <baalexander@gmail.com> (https://github.com/baalexander)")
                .shouldBeSingleton {
                    AuthorInfo("Brandon Alexander", "baalexander@gmail.com", "https://github.com/baalexander")
                }
        }

        "work if any property is not present" {
            parseAuthorString("Brandon Alexander <baalexander@gmail.com>").shouldBeSingleton {
                AuthorInfo("Brandon Alexander", "baalexander@gmail.com", null)
            }

            parseAuthorString("Brandon Alexander").shouldBeSingleton {
                AuthorInfo("Brandon Alexander", null, null)
            }

            parseAuthorString("").shouldBeSingleton {
                AuthorInfo(null, null, null)
            }
        }

        "work for mixed strings" {
            parseAuthorString("Nuxi (https://nuxi.nl/) and contributors").shouldBeSingleton {
                AuthorInfo("Nuxi and contributors", null, "https://nuxi.nl/")
            }
        }

        "return the full string as the name if no email or homepage is matched" {
            parseAuthorString("Brandon Alexander baalexander@gmail.com https://github.com/baalexander")
                .shouldBeSingleton {
                    AuthorInfo("Brandon Alexander baalexander@gmail.com https://github.com/baalexander", null, null)
                }
        }

        "handle multiple authors per string" {
            parseAuthorString("Paul Miller (http://paulmillr.com), Elan Shanker") should containExactlyInAnyOrder(
                AuthorInfo("Paul Miller", null, "http://paulmillr.com"),
                AuthorInfo("Elan Shanker", null, null)
            )
        }
    }

    "processPackageVcs()" should {
        "split a GitHub browsing URL into its components" {
            val vcsFromPackage = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/hamcrest/JavaHamcrest/hamcrest-core",
                revision = "",
                path = ""
            )

            PackageManager.processPackageVcs(vcsFromPackage) shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/hamcrest/JavaHamcrest.git",
                revision = "",
                path = "hamcrest-core"
            )
        }

        "maintain a known VCS type" {
            val vcsFromPackage = VcsInfo(
                type = VcsType.SUBVERSION,
                url = "http://svn.apache.org/repos/asf/commons/proper/codec/trunk",
                revision = ""
            )
            val homepageUrl = "http://commons.apache.org/proper/commons-codec/"

            PackageManager.processPackageVcs(vcsFromPackage, homepageUrl) shouldBe VcsInfo(
                type = VcsType.SUBVERSION,
                url = "http://svn.apache.org/repos/asf/commons/proper/codec",
                revision = "trunk"
            )
        }

        "maintain an unknown VCS type" {
            val vcsFromPackage = VcsInfo(
                type = VcsType.forName("darcs"),
                url = "http://hub.darcs.net/ross/transformers",
                revision = ""
            )

            PackageManager.processPackageVcs(vcsFromPackage) shouldBe VcsInfo(
                type = VcsType.forName("darcs"),
                url = "http://hub.darcs.net/ross/transformers",
                revision = ""
            )
        }
    }
})
