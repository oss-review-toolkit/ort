/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.helper.utils.PathExcludeGenerator.generatePathExcludes

class PathExcludeGeneratorTest : WordSpec({
    "generatePathExcludes()" should {
        "return the expected excludes for directories" {
            val files = listOf(
                "docs/file.ext",
                "src/main/file.ext",
                "src/test/file.ext"
            )

            generatePathExcludes(files).map { it.pattern } should containExactlyInAnyOrder(
                "docs/**",
                "src/test/**"
            )
        }

        "exclude only the topmost possible directory" {
            val files = listOf(
                "build/m4/file.ext"
            )

            generatePathExcludes(files).map { it.pattern } should containExactlyInAnyOrder(
                "build/**"
            )
        }

        "return excludes for a dir containing a regex special characters, e.g. the dot" {
            val files = listOf(
                ".github/file.ext"
            )

            generatePathExcludes(files).map { it.pattern } should containExactlyInAnyOrder(
                ".github/**"
            )
        }
    }
})
