/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FileMatcherTest2 : StringSpec({
    val paths = listOf(
        "README.md",
        "LICENE",
        "LICENCE",
        "LICENSE",
        "LICENES/Apache-2.0",
        "LICENCES/Apache-2.0",
        "LICENSES/Apache-2.0",
        "docs/index.md",
        "docs/getting_started.md",
        "docs/sectionA/index.md",
        "docs/sectionB/nested/index.md",
        "src/main/kotlin/Main.kt",
        "src/funTest/kotlin/Test.kt",
        "src/test/kotlin/Test.kt"
    )

    "? matches one character in a file name" {
        FileMatcher("LICEN?E").run {
            paths.filter { matches(it) }
        } shouldBe listOf(
            "LICENCE",
            "LICENSE"
        )
    }

    "? matches one character in a directory name" {
        FileMatcher("LICEN?ES/Apache-2.0").run {
            paths.filter { matches(it) }
        } shouldBe listOf(
            "LICENCES/Apache-2.0",
            "LICENSES/Apache-2.0"
        )
    }

    "* matches zero or more characters in a file name" {
        FileMatcher("LIC*").run {
            paths.filter { matches(it) }
        } shouldBe listOf(
            "LICENE",
            "LICENCE",
            "LICENSE"
        )
    }

    "* matches zero or more characters in a directory name" {
        FileMatcher("docs/*/index.md").run {
            paths.filter { matches(it) }
        } shouldBe listOf(
            "docs/sectionA/index.md"
        )
    }

    "** matches zero or more directories in a path" {
        FileMatcher("docs/**/index.md").run {
            paths.filter { matches(it) }
        } shouldBe listOf(
            "docs/index.md",
            "docs/sectionA/index.md",
            "docs/sectionB/nested/index.md"
        )
    }
})
