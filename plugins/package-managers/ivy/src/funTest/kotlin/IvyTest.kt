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

package org.ossreviewtoolkit.plugins.packagemanagers.ivy

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult

class IvyTest : StringSpec({
    "Project dependencies are detected correctly for ivy-simple" {
        val definitionFile = projectDir("ivy-simple").resolve("ivy.xml")

        val result = analyze(definitionFile).getAnalyzerResult()
        val project = result.projects.single()

        // Verify project basic info
        project.id.type shouldBe "Ivy"
        project.id.namespace shouldBe "com.example"
        project.id.name shouldBe "sample-project"
        project.id.version shouldBe "1.0.0"
        project.declaredLicenses shouldBe setOf("Apache-2.0")
        project.homepageUrl shouldBe ""

        // Verify scopes
        val scopeNames = project.scopes.map { it.name }
        scopeNames should containExactlyInAnyOrder("compile", "runtime", "test")

        // Verify dependencies per scope
        val compileDeps = project.scopes.find { it.name == "compile" }?.dependencies?.map { it.id.toCoordinates() }
        compileDeps shouldBe setOf("Maven:commons-lang:commons-lang:2.6")

        val runtimeDeps = project.scopes.find { it.name == "runtime" }?.dependencies?.map { it.id.toCoordinates() }
        runtimeDeps shouldBe setOf("Maven:org.apache.logging.log4j:log4j-core:2.17.1")

        val testDeps = project.scopes.find { it.name == "test" }?.dependencies?.map { it.id.toCoordinates() }
        testDeps shouldBe setOf("Maven:junit:junit:4.13.2")

        // Verify packages
        val packageIds = result.packages.mapTo(mutableSetOf()) { it.id.toCoordinates() }
        packageIds should containExactlyInAnyOrder(
            "Maven:commons-lang:commons-lang:2.6",
            "Maven:org.apache.logging.log4j:log4j-core:2.17.1",
            "Maven:junit:junit:4.13.2"
        )
    }

    "Dependencies are grouped by configuration" {
        val definitionFile = projectDir("ivy-simple").resolve("ivy.xml")

        val result = analyze(definitionFile).getAnalyzerResult()

        result.projects.single().scopes.map { it.name } should containExactlyInAnyOrder(
            "compile",
            "runtime",
            "test"
        )
    }

    "Project metadata is parsed correctly" {
        val definitionFile = projectDir("ivy-simple").resolve("ivy.xml")

        val result = analyze(definitionFile).getAnalyzerResult()
        val project = result.projects.single()

        project.id.namespace shouldBe "com.example"
        project.id.name shouldBe "sample-project"
        project.id.version shouldBe "1.0.0"
        project.declaredLicenses shouldBe setOf("Apache-2.0")
    }
})

private fun projectDir(name: String) = java.io.File("src/funTest/assets/projects/synthetic/$name").absoluteFile
