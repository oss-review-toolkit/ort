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
package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.PathInclude
import org.ossreviewtoolkit.model.config.PathIncludeReason
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.config.ScopeInclude
import org.ossreviewtoolkit.model.config.ScopeIncludeReason

class ExcludesUtilsTest : WordSpec({
    "isPathIncluded()" should {
        "return true if path is included and not excluded" {
            val includes = Includes(listOf(PathInclude("/some/path", PathIncludeReason.OTHER)))
            val excludes = Excludes(listOf(PathExclude("/some/other/path", PathExcludeReason.OTHER)))
            isPathIncluded("/some/path", excludes, includes) shouldBe true
        }

        "return false if path is included and excluded" {
            val includes = Includes(listOf(PathInclude("/some/path", PathIncludeReason.OTHER)))
            val excludes = Excludes(listOf(PathExclude("/some/**", PathExcludeReason.OTHER)))
            isPathIncluded("/some/path", excludes, includes) shouldBe false
        }

        "return true if path is included and if no excludes are defined" {
            val includes = Includes(listOf(PathInclude("/some/path", PathIncludeReason.OTHER)))
            val excludes = Excludes.EMPTY
            isPathIncluded("/some/path", excludes, includes) shouldBe true
        }

        "return false if path is not included and not excluded" {
            val includes = Includes(listOf(PathInclude("/some/path", PathIncludeReason.OTHER)))
            val excludes = Excludes(listOf(PathExclude("/some/other/path", PathExcludeReason.OTHER)))
            isPathIncluded("/some/second/other/path", excludes, includes) shouldBe false
        }

        "return false if path is not included and no excludes are defined" {
            val includes = Includes(listOf(PathInclude("/some/path", PathIncludeReason.OTHER)))
            val excludes = Excludes.EMPTY
            isPathIncluded("/some/other/path", excludes, includes) shouldBe false
        }

        "return true if no includes are defined and no excludes are defined" {
            val includes = Includes.EMPTY
            val excludes = Excludes.EMPTY
            isPathIncluded("/some/path", excludes, includes) shouldBe true
        }

        "return true if no includes are defined and the path is not excluded" {
            val includes = Includes.EMPTY
            val excludes = Excludes(listOf(PathExclude("/some/path", PathExcludeReason.OTHER)))
            isPathIncluded("/other/path", excludes, includes) shouldBe true
        }

        "return false if path is not included and is excluded" {
            val includes = Includes.EMPTY
            val excludes = Excludes(listOf(PathExclude("/some/path", PathExcludeReason.OTHER)))
            isPathIncluded("/some/path", excludes, includes) shouldBe false
        }
    }

    "isScopeIncluded()" should {
        "return true if scope is included and not excluded" {
            val includes = Includes(scopes = listOf(ScopeInclude("myScope", ScopeIncludeReason.OTHER)))
            val excludes = Excludes(
                scopes = listOf(ScopeExclude("excludedScope", ScopeExcludeReason.DEV_DEPENDENCY_OF))
            )
            isScopeIncluded("myScope", excludes, includes) shouldBe true
        }

        "return false if scope is included and excluded" {
            val includes = Includes(scopes = listOf(ScopeInclude("myScope", ScopeIncludeReason.OTHER)))
            val excludes = Excludes(scopes = listOf(ScopeExclude("my.*", ScopeExcludeReason.DEV_DEPENDENCY_OF)))
            isScopeIncluded("myScope", excludes, includes) shouldBe false
        }

        "return true if scope is included and if no excludes are defined" {
            val includes = Includes(scopes = listOf(ScopeInclude("myScope", ScopeIncludeReason.OTHER)))
            val excludes = Excludes.EMPTY
            isScopeIncluded("myScope", excludes, includes) shouldBe true
        }

        "return false if scope is not included and not excluded" {
            val includes = Includes(scopes = listOf(ScopeInclude("myScope", ScopeIncludeReason.OTHER)))
            val excludes = Excludes(
                scopes = listOf(ScopeExclude("myOtherScope", ScopeExcludeReason.DEV_DEPENDENCY_OF))
            )
            isScopeIncluded("aThirdScope", excludes, includes) shouldBe false
        }

        "return false if scope is not included and no excludes are defined" {
            val includes = Includes(scopes = listOf(ScopeInclude("myScope", ScopeIncludeReason.OTHER)))
            val excludes = Excludes.EMPTY
            isScopeIncluded("myOtherScope", excludes, includes) shouldBe false
        }

        "return true if no includes are defined and no excludes are defined" {
            val includes = Includes.EMPTY
            val excludes = Excludes.EMPTY
            isScopeIncluded("myScope", excludes, includes) shouldBe true
        }

        "return true if no includes are defined and the scope is not excluded" {
            val includes = Includes.EMPTY
            val excludes = Excludes(scopes = listOf(ScopeExclude("myScope", ScopeExcludeReason.DEV_DEPENDENCY_OF)))
            isScopeIncluded("myOtherScope", excludes, includes) shouldBe true
        }

        "return false if scope is not included and is excluded" {
            val includes = Includes.EMPTY
            val excludes = Excludes(scopes = listOf(ScopeExclude("myScope", ScopeExcludeReason.DEV_DEPENDENCY_OF)))
            isScopeIncluded("myScope", excludes, includes) shouldBe false
        }
    }
})
