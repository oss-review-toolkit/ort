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

package org.ossreviewtoolkit.plugins.packagecurationproviders.git

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.GitFactory

/** A fixed revision to ensure that the test is not affected by changes to the repository. */
private const val REVISION = "6fd0972895b8c10d075d8aab0c854f91157a7d0e"

@Tags("RequiresExternalTool")
class GitPackageCurationProviderFunTest : WordSpec({
    "create()" should {
        "clone the correct revision" {
            val provider = GitPackageCurationProviderFactory.create(
                repositoryUrl = ORT_CONFIG_REPOSITORY_URL,
                revision = REVISION
            )

            val workingTree = GitFactory.create().getWorkingTree(provider.repositoryDir)

            workingTree.getRevision() shouldBe REVISION
        }

        "clone the default branch if no revision is provided" {
            val provider = GitPackageCurationProviderFactory.create(ORT_CONFIG_REPOSITORY_URL)

            val git = GitFactory.create()
            val workingTree = git.getWorkingTree(provider.repositoryDir)
            val clonedRevision = workingTree.getRevision()

            git.updateWorkingTree(workingTree, "main")

            clonedRevision shouldBe workingTree.getRevision()
        }
    }

    "getCurationsFor()" should {
        val provider = GitPackageCurationProviderFactory.create(
            repositoryUrl = ORT_CONFIG_REPOSITORY_URL,
            revision = REVISION,
            path = "curations"
        )

        "return the curations from the configured path" {
            val azureCore = Identifier("NuGet:Azure:Core:1.22.0")
            val azureCoreAmqp = Identifier("NuGet:Azure.Core:Amqp:1.2.0")
            val packages = createPackagesFromIds(azureCore, azureCoreAmqp)

            val curations = provider.getCurationsFor(packages)

            curations.filter { it.isApplicable(azureCore) } shouldNot beEmpty()
            curations.filter { it.isApplicable(azureCoreAmqp) } shouldNot beEmpty()
        }

        "return curations that match the namespace of a package" {
            val xrd4j = Identifier("Maven:org.niis.xrd4j:foo:0.0.0")
            val packages = createPackagesFromIds(xrd4j)

            val curations = provider.getCurationsFor(packages)

            curations.filter { it.isApplicable(xrd4j) } shouldNot beEmpty()
        }

        "return an empty result for packages which have no curations" {
            val packages = createPackagesFromIds(Identifier("Some:Bogus:Package:Id"))

            val curations = provider.getCurationsFor(packages)

            curations should beEmpty()
        }
    }
})
