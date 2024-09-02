/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.analyzer.resolveScopes
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult

class MavenFunTest : StringSpec({
    "Root project dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/maven/pom.xml")
        val expectedResultFile = getAssetFile("projects/synthetic/maven-expected-output-root.yml")

        val result = create("Maven").resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies are detected correctly" {
        val definitionFileApp = getAssetFile("projects/synthetic/maven/app/pom.xml")
        val definitionFileLib = getAssetFile("projects/synthetic/maven/lib/pom.xml")
        val expectedResultFile = getAssetFile("projects/synthetic/maven-expected-output-app.yml")

        // app depends on lib, so we also have to pass the pom.xml of lib to resolveDependencies so that it is
        // available in the Maven.projectsByIdentifier cache. Otherwise, resolution of transitive dependencies would
        // not work.
        val managerResult = create("Maven").resolveDependencies(
            listOf(definitionFileApp, definitionFileLib),
            emptyMap()
        )

        val result = managerResult.projectResults[definitionFileApp]

        result.shouldNotBeNull()
        result should haveSize(1)
        managerResult.resolveScopes(result.single()).toYaml() should matchExpectedResult(
            expectedResultFile, definitionFileApp
        )
    }

    "External dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/maven/lib/pom.xml")
        val expectedResultFile = getAssetFile("projects/synthetic/maven-expected-output-lib.yml")

        val result = create("Maven").resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Scopes can be excluded" {
        val definitionFile = getAssetFile("projects/synthetic/maven/lib/pom.xml")
        val expectedResultFile = getAssetFile("projects/synthetic/maven-expected-output-scope-excludes.yml")

        val result = create("Maven", excludedScopes = setOf("test.*"))
            .resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Parent POM from Maven central can be resolved" {
        // Delete the parent POM from the local repository to make sure it has to be resolved from Maven central.
        Os.userHomeDirectory
            .resolve(".m2/repository/org/springframework/boot/spring-boot-starter-parent/1.5.3.RELEASE")
            .safeDeleteRecursively()

        val definitionFile = getAssetFile("projects/synthetic/maven-parent/pom.xml")
        val expectedResultFile = getAssetFile("projects/synthetic/maven-parent-expected-output-root.yml")

        val result = create("Maven").resolveSingleProject(definitionFile, resolveScopes = true)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Maven Wagon extensions can be loaded" {
        val definitionFile = getAssetFile("projects/synthetic/maven-wagon/pom.xml")
        val expectedResultFile = getAssetFile("projects/synthetic/maven-wagon-expected-output.yml")

        val result = create("Maven").resolveSingleProject(definitionFile, resolveScopes = true)

        patchActualResult(result.toYaml(), patchStartAndEndTime = true) should matchExpectedResult(
            expectedResultFile, definitionFile
        )
    }
})
