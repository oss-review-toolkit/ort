/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class PackageProviderTest : StringSpec({
    "Getting the provider for an invalid URL should return null" {
        PackageProvider.get("") should beNull()
        PackageProvider.get("    ") should beNull()
        PackageProvider.get("this-is-not-a-url") should beNull()
    }

    "Getting the provider for an unsupported URL should return null" {
        PackageProvider.get("https://example.com/") should beNull()
    }

    "Determining MAVEN_CENTRAL as the provider should work" {
        listOf(
            "https://repo.maven.apache.org/maven2/",
            "http://repo.maven.apache.org/maven2/",
            "https://repo1.maven.org/maven2/",
            "http://repo1.maven.org/maven2/"
        ).forAll {
            PackageProvider.get(it) shouldBe PackageProvider.MAVEN_CENTRAL
        }
    }
})
