/*
 * Copyright (c) 2017 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.downloader

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class VersionControlSystemTest : WordSpec({
    val vcsRoot = File("..").canonicalFile
    val relProjDir = File("src/test")
    val absProjDir = relProjDir.absoluteFile

    "For an absolute working directory, getPathToRoot()" should {
        val absVcsDir = VersionControlSystem.forDirectory(absProjDir)!!

        "work if given absolute paths" {
            absVcsDir.getPathToRoot(vcsRoot) shouldBe ""
            absVcsDir.getPathToRoot(vcsRoot.resolve("downloader/src")) shouldBe "downloader/src"
            absVcsDir.getPathToRoot(absProjDir.resolve("kotlin")) shouldBe "downloader/src/test/kotlin"
        }

        "work if given relative paths" {
            absVcsDir.getPathToRoot(File(".")) shouldBe "downloader/src/test"
            absVcsDir.getPathToRoot(File("..")) shouldBe "downloader/src"
            absVcsDir.getPathToRoot(File("kotlin")) shouldBe "downloader/src/test/kotlin"
        }
    }

    "For a relative working directory, getPathToRoot()" should {
        val relVcsDir = VersionControlSystem.forDirectory(relProjDir)!!

        "work if given absolute paths" {
            relVcsDir.getPathToRoot(vcsRoot) shouldBe ""
            relVcsDir.getPathToRoot(vcsRoot.resolve("downloader/src")) shouldBe "downloader/src"
            relVcsDir.getPathToRoot(absProjDir.resolve("kotlin")) shouldBe "downloader/src/test/kotlin"
        }

        "work if given relative paths" {
            relVcsDir.getPathToRoot(relProjDir) shouldBe "downloader/src/test"
            relVcsDir.getPathToRoot(File("..")) shouldBe "downloader/src"
            relVcsDir.getPathToRoot(File("kotlin")) shouldBe "downloader/src/test/kotlin"
        }
    }
})
