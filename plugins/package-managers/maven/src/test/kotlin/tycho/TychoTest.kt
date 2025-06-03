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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify

import java.io.File
import java.io.IOException
import java.util.jar.Manifest

import org.apache.maven.cli.MavenCli
import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.DefaultDependencyNode
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.RemoteRepository

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.PackageResolverFun
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.identifier
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

class TychoTest : WordSpec({
    afterEach {
        unmockkAll()
    }

    "mapDefinitionFiles()" should {
        "select only Tycho root projects" {
            val tychoProjectDir1 = tempdir()
            tychoProjectDir1.addTychoExtension()
            val tychoDefinitionFile1 = tychoProjectDir1.resolve("pom.xml").apply { writeText("pom-tycho1") }
            val tychoSubProjectDir = tychoProjectDir1.resolve("subproject")
            val tychoSubModule = tychoSubProjectDir.resolve("pom.xml")
            val tychoProjectDir2 = tempdir()
            tychoProjectDir2.addTychoExtension()
            val tychoDefinitionFile2 = tychoProjectDir2.resolve("pom.xml").apply { writeText("pom-tycho2") }

            val mavenProjectDir = tempdir()
            val mavenDefinitionFile = mavenProjectDir.resolve("pom.xml").apply { writeText("pom-maven") }
            val mavenSubProjectDir = mavenProjectDir.resolve("subproject")
            val mavenSubModule = mavenSubProjectDir.resolve("pom.xml")

            val definitionFiles = listOf(
                tychoDefinitionFile1,
                mavenDefinitionFile,
                tychoDefinitionFile2,
                mavenSubModule,
                tychoSubModule
            )

            val tycho = Tycho()
            val mappedDefinitionFiles = tycho.mapDefinitionFiles(tempdir(), definitionFiles, AnalyzerConfiguration())

            mappedDefinitionFiles should containExactlyInAnyOrder(tychoDefinitionFile1, tychoDefinitionFile2)
        }
    }

    "resolveDependencies()" should {
        "generate warnings about auto-generated pom files" {
            val definitionFile = tempfile()
            val rootProject = createMavenProject("root", definitionFile)
            val subProject = createMavenProject("sub")
            val projectsList = listOf(rootProject, subProject)

            val tycho = spyk(Tycho())
            injectCliMock(tycho, projectsList.toJson(), projectsList)

            val pkg1 = mockk<Package>(relaxed = true)
            val pkg2 = mockk<Package> {
                every { id } returns Identifier("Tycho:org.ossreviewtoolkit:sub-dependency:1.0.0")
                every { description } returns "POM was created by Sonatype Nexus"
            }

            val graphBuilder = mockk<DependencyGraphBuilder<DependencyNode>>(relaxed = true) {
                every { packages() } returns setOf(pkg1, pkg2)
            }

            every { tycho.createGraphBuilder(any(), any(), any(), any(), any()) } returns graphBuilder

            val results = tycho.resolveDependencies(
                tempdir(),
                definitionFile,
                Excludes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

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

            val tycho = spyk(Tycho())
            injectCliMock(tycho, buildOutput, listOf(subProject1, subProject2))

            val exception = shouldThrow<TychoBuildException> {
                tycho.resolveDependencies(
                    tempdir(),
                    definitionFile,
                    Excludes.EMPTY,
                    AnalyzerConfiguration(),
                    emptyMap()
                )
            }

            exception.message shouldContain "Tycho root project could not be built."
        }

        "generate an issue if the Maven build had a non-zero exit code" {
            val definitionFile = tempfile()
            val rootProject = createMavenProject("root", definitionFile)
            val subProject = createMavenProject("sub")
            val projectsList = listOf(rootProject, subProject)

            val tycho = spyk(Tycho())
            injectCliMock(tycho, projectsList.toJson(), projectsList, exitCode = 1)

            val results = tycho.resolveDependencies(
                tempdir(),
                definitionFile,
                Excludes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

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

            val tycho = spyk(Tycho())
            injectCliMock(tycho, projectsList.take(2).toJson(), projectsList, exitCode = 1)

            val results = tycho.resolveDependencies(
                tempdir(),
                definitionFile,
                Excludes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

            val rootResults = results.single { it.project.id.name == "root" }
            rootResults.issues.forAll { issue ->
                issue.source shouldBe "Tycho"
                issue.severity shouldBe Severity.ERROR
            }

            val regExBuildProjectError = Regex(".*for project '(.*)'.*")
            val projectIssues = rootResults.issues.mapNotNull { issue ->
                regExBuildProjectError.matchEntire(issue.message)?.groupValues?.get(1)
            }

            projectIssues should containExactlyInAnyOrder(
                subProject2.identifier("Tycho").toCoordinates(),
                subProject3.identifier("Tycho").toCoordinates()
            )
        }

        "set up a P2ArtifactResolver correctly" {
            val definitionFile = tempfile()
            val rootProject = createMavenProject("root", definitionFile)
            val subProject = createMavenProject("sub")
            val projectsList = listOf(rootProject, subProject)

            mockkObject(TargetHandler)
            val targetHandler = mockk<TargetHandler>()
            every { TargetHandler.create(definitionFile.parentFile) } returns targetHandler

            val tycho = spyk(Tycho())
            injectCliMock(tycho, projectsList.toJson(), projectsList)

            tycho.resolveDependencies(
                tempdir(),
                definitionFile,
                Excludes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

            val slotProjects = slot<Collection<MavenProject>>()
            verify {
                P2ArtifactResolver.create(targetHandler, capture(slotProjects))
            }

            slotProjects.captured shouldContainExactlyInAnyOrder projectsList
        }

        "add issues from the resolver to the root project" {
            val definitionFile = tempfile()
            val rootProject = createMavenProject("root", definitionFile)
            val subProject = createMavenProject("sub")
            val projectsList = listOf(rootProject, subProject)

            val issues = listOf(
                Issue(source = "TychoResolver", message = "Something went wrong with repository 'repo1'."),
                Issue(source = "TychoResolver", message = "more trouble ahead", severity = Severity.HINT)
            )
            val resolver = mockk<P2ArtifactResolver> {
                every { resolverIssues } returns issues
                every { isFeature(any()) } returns false
            }

            val tycho = spyk(Tycho())
            injectCliMock(tycho, projectsList.toJson(), projectsList, resolver = resolver)

            val results = tycho.resolveDependencies(
                tempdir(),
                definitionFile,
                Excludes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

            val rootResults = results.single { it.project.id.name == "root" }
            rootResults.issues shouldContainExactlyInAnyOrder issues

            verify(atLeast = 1) {
                // Make sure that the resolver is used for the feature check function.
                resolver.isFeature(any())
            }
        }
    }

    "tychoPackageResolverFunc()" should {
        "return the package from the delegate function" {
            val dependency = mockk<DependencyNode>()
            val pkg = mockk<Package>()
            val delegate: PackageResolverFun = { node ->
                node shouldBe dependency
                pkg
            }

            val resolver = tychoPackageResolverFun(delegate, mockk(), mockk(), mockk())

            resolver(dependency) shouldBe pkg
        }

        "throw the original exception if no artifact is available in the local repository" {
            val resolver = createResolverFunWithRepositoryHelper {
                every { osgiManifest(any()) } returns null
            }

            shouldThrow<RuntimeException> {
                resolver(testDependency())
            } shouldBe resolveException
        }

        "return a Package with undefined metadata for a jar in the local repository without manifest entries" {
            val resolver = createResolverFunWithRepositoryHelper {
                every { osgiManifest(any()) } returns Manifest()
            }

            val pkg = resolver(testDependency())

            with(pkg) {
                id shouldBe testArtifactIdentifier
                description should io.kotest.matchers.string.beEmpty()
                binaryArtifact shouldBe RemoteArtifact.EMPTY
                sourceArtifact shouldBe RemoteArtifact.EMPTY
                vcs shouldBe VcsInfo.EMPTY
                vcsProcessed shouldBe VcsInfo.EMPTY
                declaredLicenses should beEmpty()
                declaredLicensesProcessed.spdxExpression should beNull()
                concludedLicense should beNull()
                authors should beEmpty()
            }
        }

        "return a Package with metadata obtained from an OSGi bundle in the local repository" {
            val bundleProperties = mapOf(
                "Bundle-Description" to "Package description",
                "Bundle-License" to "The Apache License",
                "Bundle-Vendor" to "Package vendor",
                "Bundle-DocURL" to "https://example.com/package"
            )

            val resolver = createResolverFunWithRepositoryHelper {
                every { osgiManifest(any()) } returns createManifest(bundleProperties)
            }

            val pkg = resolver(testDependency())

            with(pkg) {
                id shouldBe testArtifactIdentifier
                description shouldBe bundleProperties["Bundle-Description"]
                binaryArtifact shouldBe RemoteArtifact.EMPTY
                sourceArtifact shouldBe RemoteArtifact.EMPTY
                vcs shouldBe VcsInfo.EMPTY
                vcsProcessed shouldBe VcsInfo.EMPTY
                declaredLicenses should containExactlyInAnyOrder(bundleProperties["Bundle-License"])
                declaredLicensesProcessed shouldBe ProcessedDeclaredLicense(
                    spdxExpression = SpdxExpression.parse("Apache-2.0"),
                    mapped = mapOf("The Apache License" to SpdxExpression.parse("Apache-2.0"))
                )
                concludedLicense should beNull()
                authors should containExactlyInAnyOrder(bundleProperties["Bundle-Vendor"])
                homepageUrl shouldBe "https://example.com/package"
            }
        }

        "handle Tycho identifiers that need to be mapped to Maven dependencies" {
            val originalArtifact = mockk<Artifact>()
            val mappedArtifact = mockk<Artifact>()
            val repo1 = mockk<RemoteRepository>()
            val repo2 = mockk<RemoteRepository>()
            val dependency = DefaultDependencyNode(originalArtifact).apply {
                repositories = listOf(repo1, repo2)
            }

            val pkg = mockk<Package>()
            val delegate: PackageResolverFun = { node ->
                if (node == dependency) {
                    throw IOException("Test exception: Unresolvable dependency.")
                }

                node.artifact shouldBe mappedArtifact
                node.repositories should containExactlyInAnyOrder(repo1, repo2)

                pkg
            }

            val targetHandler = mockk<TargetHandler> {
                every { mapToMavenDependency(originalArtifact) } returns mappedArtifact
            }

            val resolver = tychoPackageResolverFun(delegate, mockk(), mockk(), targetHandler)

            resolver(dependency) shouldBe pkg
        }
    }

    "createPackageFromManifest()" should {
        "parse a source reference with only a connection" {
            val manifest = createManifest(
                mapOf(
                    "Eclipse-SourceReferences" to "https://git.eclipse.org/gitroot/platform/eclipse.platform.debug.git"
                )
            )

            val pkg = createPackageFromManifest(testArtifact, manifest, createResolverMock())

            pkg.vcs shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://git.eclipse.org/gitroot/platform/eclipse.platform.debug.git",
                revision = ""
            )
            pkg.vcsProcessed shouldBe pkg.vcs
        }

        "parse a source reference with a connection and a tag" {
            val manifest = createManifest(
                mapOf(
                    "Eclipse-SourceReferences" to
                        "https://git.eclipse.org/gitroot/platform/eclipse.platform.debug.git;tag=v20210901-0700"
                )
            )

            val pkg = createPackageFromManifest(testArtifact, manifest, createResolverMock())

            pkg.vcs shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://git.eclipse.org/gitroot/platform/eclipse.platform.debug.git",
                revision = "v20210901-0700"
            )
            pkg.vcsProcessed shouldBe pkg.vcs
        }

        "parse a source reference with a connection and a tag in quotes" {
            val manifest = createManifest(
                mapOf(
                    "Eclipse-SourceReferences" to
                        "https://git.eclipse.org/gitroot/platform/eclipse.platform.debug.git;tag=\"v20210901-0700\""
                )
            )

            val pkg = createPackageFromManifest(testArtifact, manifest, createResolverMock())

            pkg.vcs shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://git.eclipse.org/gitroot/platform/eclipse.platform.debug.git",
                revision = "v20210901-0700"
            )
            pkg.vcsProcessed shouldBe pkg.vcs
        }

        "parse a source reference with a connection and a path" {
            val manifest = createManifest(
                mapOf(
                    "Eclipse-SourceReferences" to
                        "https://git.eclipse.org/gitroot/platform/eclipse.platform.debug.git;path=\"bundles/debug\""
                )
            )

            val pkg = createPackageFromManifest(testArtifact, manifest, createResolverMock())

            pkg.vcs shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://git.eclipse.org/gitroot/platform/eclipse.platform.debug.git",
                revision = "",
                path = "bundles/debug"
            )
            pkg.vcsProcessed shouldBe pkg.vcs
        }

        "handle source references with unexpected fields" {
            val manifest = createManifest(
                mapOf(
                    "Eclipse-SourceReferences" to
                        "https://git.eclipse.org/eclipse.platform.debug.git;tag=\"v20210901-0700\";foo=bar"
                )
            )

            val pkg = createPackageFromManifest(testArtifact, manifest, createResolverMock())

            pkg.vcs shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://git.eclipse.org/eclipse.platform.debug.git",
                revision = "v20210901-0700"
            )
            pkg.vcsProcessed shouldBe pkg.vcs
        }

        "handle multiple source references" {
            val manifest = createManifest(
                mapOf(
                    "Eclipse-SourceReferences" to "https://git.eclipse.org/eclipse.platform.debug1.git," +
                        "git://git.eclipse.org/eclipse.platform.debug2.git;tag=\"v20210901-0700\""
                )
            )

            val pkg = createPackageFromManifest(testArtifact, manifest, createResolverMock())

            pkg.vcs shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://git.eclipse.org/eclipse.platform.debug1.git",
                revision = ""
            )
            pkg.vcsProcessed shouldBe pkg.vcs
        }

        "process package VCS information" {
            val manifest = createManifest(
                mapOf("Bundle-DocURL" to "https://example.com/package.git")
            )

            val pkg = createPackageFromManifest(testArtifact, manifest, createResolverMock())

            pkg.vcs shouldBe VcsInfo.EMPTY
            pkg.vcsProcessed shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://example.com/package.git",
                revision = ""
            )
        }

        "obtain the binary artifact from the tracker" {
            val binaryArtifact = RemoteArtifact.EMPTY.copy(url = "https://example.com/binary")
            val tracker = createResolverMock(binaryArtifact = binaryArtifact)

            val pkg = createPackageFromManifest(testArtifact, Manifest(), tracker)

            pkg.binaryArtifact shouldBe binaryArtifact
        }

        "obtain the source artifact from the tracker" {
            val sourceArtifact = RemoteArtifact.EMPTY.copy(url = "https://example.com/source")
            val tracker = createResolverMock(sourceArtifact = sourceArtifact)

            val pkg = createPackageFromManifest(testArtifact, Manifest(), tracker)

            pkg.sourceArtifact shouldBe sourceArtifact
        }
    }
})

/** The group ID of the test project. For OSGi artifacts this typically refers to a repository. */
private const val TEST_GROUP_ID = "p2.some.repo"

/** ID of a test bundle serving as a dependency. */
private const val TEST_ARTIFACT_ID = "org.ossreviewtoolkit.test.bundle"

/** The version number of the test dependency. */
private const val TEST_VERSION = "50.1.2"

/** The expected package identifier generated for the test artifact. */
private val testArtifactIdentifier = Identifier("Maven", TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION)

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
 * Create a mock for a [MavenCli] object and configure the given [tycho] spy to use it. The mock CLI simulates a
 * Maven build that produces the given [buildOutput] and detects the given [projectsList]. It returns the given
 * [exitCode]. In addition, a mock for the [P2ArtifactResolver] is prepared to be used by the Tycho instance.
 */
private fun injectCliMock(
    tycho: Tycho,
    buildOutput: String,
    projectsList: List<MavenProject>,
    exitCode: Int = 0,
    resolver: P2ArtifactResolver = mockk(relaxed = true)
): MavenCli {
    mockkObject(P2ArtifactResolver)
    every { P2ArtifactResolver.create(any(), any()) } returns resolver

    val cli = mockk<MavenCli> {
        every { doMain(any(), any(), any(), any()) } answers {
            val args = firstArg<Array<String>>()
            val outputFileArg = args.single { arg -> arg.startsWith("-DoutputFile") }
            val outputFile = File(outputFileArg.substringAfter("="))
            outputFile.writeText(buildOutput)
            exitCode
        }
    }

    val session = mockk<MavenSession> {
        every { projects } returns projectsList
    }

    every { tycho.createMavenCli(any()) } answers {
        val collector = firstArg<TychoProjectsCollector>()
        collector.afterSessionEnd(session)
        cli
    }

    return cli
}

/** The test artifact used by many tests. */
private val testArtifact = DefaultArtifact(TEST_GROUP_ID, TEST_ARTIFACT_ID, "jar", TEST_VERSION)

/**
 * Return a mock [DependencyNode] that is configured to return the properties of the test artifact.
 */
private fun testDependency(): DependencyNode =
    mockk {
        every { artifact } returns testArtifact
    }

/** An exception that is thrown by the delegate resolver function to force the resolving from the local repository. */
private val resolveException = RuntimeException("Test exception: Could not resolve the test artifact.")

/**
 * Return a [PackageResolverFun] to be tested that is configured with a delegate function that throws a well-defined
 * exception. First create a mock [LocalRepositoryHelper] and invoke the given [block] to prepare it accordingly for
 * the current test case.
 */
private fun createResolverFunWithRepositoryHelper(block: LocalRepositoryHelper.() -> Unit): PackageResolverFun {
    val helper = mockk<LocalRepositoryHelper>(block = block)
    val resolver = createResolverMock()
    val targetHandler = mockk<TargetHandler> {
        every { mapToMavenDependency(any()) } returns null
    }

    val delegateResolverFun: PackageResolverFun = { throw resolveException }
    return tychoPackageResolverFun(delegateResolverFun, helper, resolver, targetHandler)
}

/**
 * Create a new [Manifest] that contains the given [entries].
 */
private fun createManifest(entries: Map<String, String>): Manifest =
    Manifest().apply {
        entries.forEach { (key, value) -> mainAttributes.putValue(key, value) }
    }

/**
 * Create a mock [P2ArtifactResolver] that is prepared to return the given [binaryArtifact] and [sourceArtifact] when
 * queried for the test artifact.
 */
private fun createResolverMock(
    binaryArtifact: RemoteArtifact = RemoteArtifact.EMPTY,
    sourceArtifact: RemoteArtifact = RemoteArtifact.EMPTY
): P2ArtifactResolver {
    val testArtifact = testArtifact

    return mockk {
        every { getBinaryArtifactFor(testArtifact) } returns binaryArtifact
        every { getSourceArtifactFor(testArtifact) } returns sourceArtifact
        every { isBinary(any()) } returns false
    }
}
