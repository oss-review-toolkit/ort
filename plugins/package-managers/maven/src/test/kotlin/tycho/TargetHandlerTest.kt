/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.tycho

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should

import java.io.File

class TargetHandlerTest : WordSpec({
    "repositoryUrls" should {
        "be empty if no target files are found" {
            val projectRoot = tempdir()
            val targetHandler = TargetHandler.create(projectRoot)

            targetHandler.repositoryUrls should beEmpty()
        }

        "collect P2 repositories from target files" {
            val root = tempdir()
            val targetFile1 = File("src/test/assets/tycho.target")
            val targetFile2 = File("src/test/assets/tycho.other.target")
            val module1 = root.resolve("module1").also { it.mkdirs() }
            val module2 = root.resolve("module2").also { it.mkdirs() }
            val subModule = module2.resolve("subModule.target").also { it.mkdirs() }
            targetFile1.copyTo(module1.resolve("tycho.target"))
            targetFile2.copyTo(subModule.resolve("tycho.other.target"))

            val targetHandler = TargetHandler.create(root)

            targetHandler.repositoryUrls shouldContainExactlyInAnyOrder listOf(
                "https://p2.example.com/repo/download.eclipse.org/modeling/tmf/xtext/updates/releases/2.37.0/",
                "https://p2.example.org/repo/download.eclipse.org/modeling/emft/mwe/updates/releases/2.20.0/",
                "https://p2.example.com/repository/download.eclipse.org/releases/2024-12",
                "https://p2.other.example.com/repo/other/test/"
            )
        }
    }
})
