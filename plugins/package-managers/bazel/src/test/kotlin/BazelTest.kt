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

package org.ossreviewtoolkit.plugins.packagemanagers.bazel

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.test.getAssetFile

class BazelTest : WordSpec({
    "transformBazelVersion()" should {
        "remove everything except for the version number" {
            val bazelVersionOutput = """
                Bazelisk version: development
                WARNING: Invoking Bazel in batch mode since it is not invoked from within a workspace (below a directory having a WORKSPACE file).
                OpenJDK 64-Bit Server VM warning: Options -Xverify:none and -noverify were deprecated in JDK 13 and will likely be removed in a future release.
                Build label: 7.0.1
                Build target: @@//src/main/java/com/google/devtools/build/lib/bazel:BazelServer
                Build time: Thu Jan 18 18:05:58 2024 (1705601158)
                Build timestamp: 1705601158
                Build timestamp as int: 1705601158
            """.trimIndent()

            val result = transformBazelVersion(bazelVersionOutput)
            result shouldBe "7.0.1"
        }
    }

    "Bazel package manager" should {
        "support local registry" {
            val projectAssets = getAssetFile("projects/synthetic/bazel-local-registry/")
            val projectDir = tempdir()
            projectAssets.copyRecursively(projectDir)
            val definitionFile = projectDir.resolve("MODULE.bazel")

            val bazel = Bazel("bazel", projectDir, AnalyzerConfiguration(), RepositoryConfiguration())

            val resolvedDependencies = bazel.resolveDependencies(definitionFile, emptyMap())

            resolvedDependencies shouldNot beEmpty()
        }
    }
})
