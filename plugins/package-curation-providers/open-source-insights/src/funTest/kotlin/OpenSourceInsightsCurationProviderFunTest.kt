/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.opensourceinsights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingle
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.time.Instant

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package

class OpenSourceInsightsCurationProviderFunTest : StringSpec({
    val provider by lazy { OpenSourceInsightsCurationProviderFactory.create() }

    "An existing Maven package has the published timestamp" {
        val packages = createPackagesFromIds("Maven:javax.servlet:javax.servlet-api:3.1.0")

        val curations = provider.getCurationsFor(packages)

        with(curations.shouldBeSingle().data) {
            publishedAt shouldBe Instant.parse("2013-04-25T23:52:37Z")
        }
    }

    "An non-existing Maven package is handled properly" {
        val packages = createPackagesFromIds("Maven:javax.servlet:javax.servlet-api:310")

        val curations = provider.getCurationsFor(packages)

        curations should beEmpty()
    }

    "An existing NPM package has the published timestamp" {
        val packages = createPackagesFromIds("NPM:@colors:colors:1.5.0")

        val curations = provider.getCurationsFor(packages)

        with(curations.shouldBeSingle().data) {
            publishedAt shouldBe Instant.parse("2022-02-12T07:39:04Z")
        }
    }
})

private fun createPackagesFromIds(vararg ids: String) = ids.map { Package.EMPTY.copy(id = Identifier(it)) }
