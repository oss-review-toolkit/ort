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

package org.ossreviewtoolkit.plugins.packagemanagers.maven

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify

import java.io.File
import java.io.PrintStream

import org.apache.maven.cli.MavenCli
import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject

import org.eclipse.aether.graph.DependencyNode

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.DependencyTreeMojoNode
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.JSON
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.identifier

class TychoTest : WordSpec({
    "mapDefinitionFiles()" should {
        "select only Tycho root projects" {
            val tychoProjectDir1 = tempdir()
            tychoProjectDir1.addTychoExtension()
            val tychoDefinitionFile1 = tychoProjectDir1.resolve("pom.xml").also { it.writeText("pom-tycho1") }
            val tychoSubProjectDir = tychoProjectDir1.resolve("subproject")
            val tychoSubModule = tychoSubProjectDir.resolve("pom.xml")
            val tychoProjectDir2 = tempdir()
            tychoProjectDir2.addTychoExtension()
            val tychoDefinitionFile2 = tychoProjectDir2.resolve("pom.xml").also { it.writeText("pom-tycho2") }

            val mavenProjectDir = tempdir()
            val mavenDefinitionFile = mavenProjectDir.resolve("pom.xml").also { it.writeText("pom-maven") }
            val mavenSubProjectDir = mavenProjectDir.resolve("subproject")
            val mavenSubModule = mavenSubProjectDir.resolve("pom.xml")

            val definitionFiles = listOf(
                tychoDefinitionFile1,
                mavenDefinitionFile,
                tychoDefinitionFile2,
                mavenSubModule,
                tychoSubModule
            )

            val tycho = Tycho("Tycho", tempdir(), mockk(relaxed = true), mockk(relaxed = true))
            val mappedDefinitionFiles = tycho.mapDefinitionFiles(definitionFiles)

            mappedDefinitionFiles shouldContainExactlyInAnyOrder listOf(tychoDefinitionFile1, tychoDefinitionFile2)
        }
    }

    "resolveDependencies()" should {
        "generate warnings about auto-generated pom files" {
            val definitionFile = tempfile()
            val rootProject = createMavenProject("root", definitionFile)
            val subProject = createMavenProject("sub")
            val projectsList = listOf(rootProject, subProject)

            val tycho = spyk(Tycho("Tycho", tempdir(), mockk(relaxed = true), mockk(relaxed = true)))
            injectCliMock(tycho, projectsList.toJson(), projectsList)

            val pkg1 = mockk<Package>(relaxed = true)
            val pkg2 = mockk<Package> {
                every { id } returns Identifier("Tycho:org.ossreviewtoolkit:sub-dependency:1.0.0")
                every { description } returns "POM was created by Sonatype Nexus"
            }

            val graphBuilder = mockk<DependencyGraphBuilder<DependencyNode>>(relaxed = true) {
                every { packages() } returns setOf(pkg1, pkg2)
            }

            every { tycho.createGraphBuilder(any(), any()) } returns graphBuilder

            val results = tycho.resolveDependencies(definitionFile, emptyMap())

            val (rootResults, subResults) = results.partition { it.project.id.name == "root" }

            with(rootResults.single().issues.single()) {
                severity shouldBe Severity.HINT
                message shouldContain pkg2.id.toCoordinates()
                message shouldContain "auto-generated POM"
                source shouldBe "Tycho"
            }

            subResults.single().issues should beEmpty()
        }

        "throw an exception if the root project could not be built" {
            val definitionFile = tempfile()
            val rootProject = createMavenProject("root", definitionFile)
            val subProject1 = createMavenProject("sub1")
            val subProject2 = createMavenProject("sub2")

            val buildOutput = listOf(subProject2, rootProject, subProject1).toJson()

            val tycho = spyk(Tycho("Tycho", tempdir(), mockk(relaxed = true), mockk(relaxed = true)))
            injectCliMock(tycho, buildOutput, listOf(subProject1, subProject2))

            val exception = shouldThrow<TychoBuildException> {
                tycho.resolveDependencies(definitionFile, emptyMap())
            }

            exception.message shouldContain "Tycho root project could not be built."
        }

        "generate an issue if the Maven build had a non-zero exit code" {
            val definitionFile = tempfile()
            val rootProject = createMavenProject("root", definitionFile)
            val subProject = createMavenProject("sub")
            val projectsList = listOf(rootProject, subProject)

            val tycho = spyk(Tycho("Tycho", tempdir(), mockk(relaxed = true), mockk(relaxed = true)))
            injectCliMock(tycho, projectsList.toJson(), projectsList, exitCode = 1)

            val results = tycho.resolveDependencies(definitionFile, emptyMap())

            val rootResults = results.single { it.project.id.name == "root" }
            with(rootResults.issues.single()) {
                severity shouldBe Severity.ERROR
                message shouldContain "Maven build failed"
                source shouldBe "Tycho"
            }
        }

        "generate issues for sub projects that do not occur in the build output" {
            val definitionFile = tempfile()
            val rootProject = createMavenProject("root", definitionFile)
            val subProject1 = createMavenProject("sub1")
            val subProject2 = createMavenProject("sub2")
            val subProject3 = createMavenProject("sub3")
            val projectsList = listOf(rootProject, subProject1, subProject2, subProject3)

            val tycho = spyk(Tycho("Tycho", tempdir(), mockk(relaxed = true), mockk(relaxed = true)))
            injectCliMock(tycho, projectsList.take(2).toJson(), projectsList, exitCode = 1)

            val results = tycho.resolveDependencies(definitionFile, emptyMap())

            val rootResults = results.single { it.project.id.name == "root" }
            rootResults.issues.forAll { issue ->
                issue.source shouldBe "Tycho"
                issue.severity shouldBe Severity.ERROR
            }

            val regExBuildProjectError = Regex(".*for project '(.*)'.*")
            val projectIssues = rootResults.issues.mapNotNull { issue ->
                regExBuildProjectError.matchEntire(issue.message)?.groupValues?.get(1)
            }

            projectIssues shouldContainExactlyInAnyOrder listOf(
                subProject2.identifier("Tycho").toCoordinates(),
                subProject3.identifier("Tycho").toCoordinates()
            )
        }

        "exclude projects from the build according to path excludes" {
            val analysisRoot = tempdir()
            val tychoRoot = analysisRoot.createSubModule("tycho-root")
            tychoRoot.createSubModule("tycho-sub1")
            tychoRoot.createSubModule("tycho-sub2")
            tychoRoot.createSubModule("tycho-excluded-sub1")
            val module = tychoRoot.createSubModule("tycho-excluded-sub2")
            module.createSubModule("tycho-excluded-sub2-sub")
            val rootProject = createMavenProject("root", tychoRoot.pom)

            val excludes = Excludes(
                paths = listOf(
                    PathExclude("tycho-root/tycho-excluded-sub1", PathExcludeReason.EXAMPLE_OF),
                    PathExclude("tycho-root/tycho-excluded-sub2/**", PathExcludeReason.TEST_OF),
                    PathExclude("other-root/**", PathExcludeReason.EXAMPLE_OF)
                )
            )
            val analyzerConfig = AnalyzerConfiguration(skipExcluded = true)
            val repositoryConfig = RepositoryConfiguration(excludes = excludes)

            val tycho = spyk(Tycho("Tycho", analysisRoot, analyzerConfig, repositoryConfig))
            val cli = injectCliMock(tycho, listOf(rootProject).toJson(), listOf(rootProject))

            tycho.resolveDependencies(tychoRoot.pom, emptyMap())

            val slotArgs = slot<Array<String>>()
            verify {
                cli.doMain(capture(slotArgs), any(), any(), any())
            }

            with(slotArgs.captured) {
                val indexPl = indexOf("-pl")
                indexPl shouldBeGreaterThan -1
                val excludedProjects = get(indexPl + 1).split(",")
                excludedProjects shouldContainExactlyInAnyOrder listOf(
                    "!tycho-excluded-sub1",
                    "!tycho-excluded-sub2",
                    "!tycho-excluded-sub2/tycho-excluded-sub2-sub"
                )
            }
        }

        "not add a -pl option if no projects are excluded" {
            val analysisRoot = tempdir()
            val tychoRoot = analysisRoot.createSubModule("tycho-root")
            tychoRoot.createSubModule("tycho-sub1")
            tychoRoot.createSubModule("tycho-sub2")
            val rootProject = createMavenProject("root", tychoRoot.pom)

            val excludes = Excludes(paths = listOf(PathExclude("other-root/**", PathExcludeReason.EXAMPLE_OF)))
            val analyzerConfig = AnalyzerConfiguration(skipExcluded = true)
            val repositoryConfig = RepositoryConfiguration(excludes = excludes)

            val tycho = spyk(Tycho("Tycho", analysisRoot, analyzerConfig, repositoryConfig))
            val cli = injectCliMock(tycho, listOf(rootProject).toJson(), listOf(rootProject))

            tycho.resolveDependencies(tychoRoot.pom, emptyMap())

            val slotArgs = slot<Array<String>>()
            verify {
                cli.doMain(capture(slotArgs), any(), any(), any())
            }

            slotArgs.captured.toList() shouldNot contain("-pl")
        }

        "not exclude projects if skipExcluded is false" {
            val analysisRoot = tempdir()
            val tychoRoot = analysisRoot.createSubModule("tycho-root")
            tychoRoot.createSubModule("tycho-sub1")
            tychoRoot.createSubModule("tycho-sub2")
            tychoRoot.createSubModule("tycho-excluded-sub1")
            val rootProject = createMavenProject("root", tychoRoot.pom)

            val excludes = Excludes(
                paths = listOf(PathExclude("tycho-root/tycho-excluded-sub1", PathExcludeReason.EXAMPLE_OF))
            )
            val analyzerConfig = AnalyzerConfiguration()
            val repositoryConfig = RepositoryConfiguration(excludes = excludes)

            val tycho = spyk(Tycho("Tycho", analysisRoot, analyzerConfig, repositoryConfig))
            val cli = injectCliMock(tycho, listOf(rootProject).toJson(), listOf(rootProject))

            tycho.resolveDependencies(tychoRoot.pom, emptyMap())

            val slotArgs = slot<Array<String>>()
            verify {
                cli.doMain(capture(slotArgs), any(), any(), any())
            }

            slotArgs.captured.toList() shouldNot contain("-pl")
        }
    }
})

/**
 * Create a mock [MavenProject] with default coordinates and the given [name] and optional [definitionFile].
 */
private fun TestConfiguration.createMavenProject(name: String, definitionFile: File = tempfile()): MavenProject =
    mockk(relaxed = true) {
        every { groupId } returns "org.ossreviewtoolkit"
        every { artifactId } returns name
        every { version } returns "1.0.0"
        every { file } returns definitionFile
        every { id } returns "org.ossreviewtoolkit:$name:jar:1.0.0"
        every { parent } returns null
    }

/**
 * Return a [DependencyTreeMojoNode] with the properties of this [MavenProject].
 */
private fun MavenProject.toDependencyTreeMojoNode(): DependencyTreeMojoNode =
    DependencyTreeMojoNode(groupId, artifactId, version, "jar", "compile", "")

/**
 * Generate JSON output analogously to the Maven Dependency Plugin for this list of [MavenProject]s. Here only the
 * root project nodes are relevant; dependencies are not included.
 */
private fun Collection<MavenProject>.toJson(): String =
    joinToString("\n") { JSON.encodeToString(it.toDependencyTreeMojoNode()) }

/**
 * Create a mock for a [MavenCli] object and configure the given [tycho] spi to use it. The mock CLI simulates a
 * Maven build that produces the given [buildOutput] and detects the given [projectsList]. It returns the given
 * [exitCode].
 */
private fun injectCliMock(
    tycho: Tycho,
    buildOutput: String,
    projectsList: List<MavenProject>,
    exitCode: Int = 0
): MavenCli {
    val cli = mockk<MavenCli> {
        every { doMain(any(), any(), any(), any()) } returns exitCode
    }

    val session = mockk<MavenSession> {
        every { projects } returns projectsList
    }

    every { tycho.createMavenCli(any(), any()) } answers {
        val collector = firstArg<TychoProjectsCollector>()
        collector.afterSessionEnd(session)

        val out = secondArg<PrintStream>()
        out.println(buildOutput)
        cli
    }

    return cli
}

/**
 * Return a reference to the Maven pom file in this folder.
 */
private val File.pom: File
    get() = resolve("pom.xml")

/**
 * Create a sub folder with the given [name] and a pom file to simulate a Maven module. Return the created folder
 * for the module.
 */
private fun File.createSubModule(name: String): File =
    resolve(name).apply {
        mkdirs()
        pom.also { it.writeText("pom-$name") }
    }
