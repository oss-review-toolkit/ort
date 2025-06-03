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

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.eclipse.aether.artifact.DefaultArtifact

class TargetHandlerTest : WordSpec({
    "repositoryUrls" should {
        "be empty if no target files are found" {
            val projectRoot = tempdir()
            val targetHandler = TargetHandler.create(projectRoot)

            targetHandler.repositoryUrls should beEmpty()
        }

        "collect P2 repositories from target files" {
            val targetHandler = createTargetHandlerWithTargetFiles()

            targetHandler.repositoryUrls should containExactlyInAnyOrder(
                "https://p2.example.com/repo/download.eclipse.org/modeling/tmf/xtext/updates/releases/2.37.0/",
                "https://p2.example.org/repo/download.eclipse.org/modeling/emft/mwe/updates/releases/2.20.0/",
                "https://p2.example.com/repository/download.eclipse.org/releases/2024-12",
                "https://p2.other.example.com/repo/other/test/"
            )
        }
    }

    "mapToMavenDependency()" should {
        "return null if an artifact cannot be mapped to a Maven dependency" {
            val tychoArtifact = DefaultArtifact("groupId", "artifactId", "ext", "version")
            val targetHandler = TargetHandler.create(tempdir())

            targetHandler.mapToMavenDependency(tychoArtifact) should beNull()
        }

        "return the correct Maven dependency if an artifact can be mapped" {
            val tychoArtifact = DefaultArtifact("p2.eclipse.plugin", "ch.qos.logback.logback-classic", "jar", "1.5.6")
            val targetHandler = createTargetHandlerWithTargetFiles()

            targetHandler.mapToMavenDependency(tychoArtifact).shouldNotBeNull {
                groupId shouldBe "ch.qos.logback"
                artifactId shouldBe "logback-classic"
                version shouldBe "1.5.6"
            }
        }
    }

    "featureIds" should {
        "be empty if no target files are found" {
            val projectRoot = tempdir()
            val targetHandler = TargetHandler.create(projectRoot)

            targetHandler.featureIds should beEmpty()
        }

        "collect feature IDs from target files" {
            val targetHandler = createTargetHandlerWithTargetFiles()

            targetHandler.featureIds should containExactlyInAnyOrder(
                "maven.libraries.with.transitives",
                "some.feature"
            )
        }
    }
})

/**
 * Create a new [TargetHandler] instance that is initialized from a folder structure which contains some test
 * target files.
 */
private fun TestConfiguration.createTargetHandlerWithTargetFiles(): TargetHandler {
    val root = tempdir()
    val targetFile1 = File("src/test/assets/tycho.target")
    val targetFile2 = File("src/test/assets/tycho.other.target")
    val module1 = root.resolve("module1").apply { mkdirs() }
    val module2 = root.resolve("module2").apply { mkdirs() }
    val subModule = module2.resolve("subModule.target").apply { mkdirs() }
    targetFile1.copyTo(module1.resolve("tycho.target"))
    targetFile2.copyTo(subModule.resolve("tycho.other.target"))

    return TargetHandler.create(root)
}
