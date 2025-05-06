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
import io.kotest.inspectors.forAll
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import kotlin.collections.set

import org.eclipse.aether.artifact.DefaultArtifact

class LocalRepositoryHelperTest : WordSpec({
    "folderForOsgiArtifact()" should {
        "resolve the folder in the local Maven repository" {
            val root = tempdir()
            val folder = root.createRepositoryFolder()

            val helper = LocalRepositoryHelper(root)
            helper.folderForOsgiArtifact(testArtifact) shouldBe folder
        }

        "resolve the folder for a binary artifact in the local Maven repository" {
            val root = tempdir()
            val folder = root.createRepositoryFolder(repoPath = "binary")

            val helper = LocalRepositoryHelper(root)
            helper.folderForOsgiArtifact(testArtifact, isBinary = true) shouldBe folder
        }

        "return null if the artifact is not found in the local Maven repository" {
            val root = tempdir()
            val helper = LocalRepositoryHelper(root)

            helper.folderForOsgiArtifact(testArtifact) shouldBe null
        }
    }

    "fileForOsgiArtifact()" should {
        "resolve the artifact file in the local Maven repository" {
            val root = tempdir()
            val folder = root.createRepositoryFolder()
            val jar = folder.createJar("$TEST_ARTIFACT_ID-$TEST_VERSION", null)

            val helper = LocalRepositoryHelper(root)
            helper.fileForOsgiArtifact(testArtifact) shouldBe jar
        }

        "resolve the artifact file for a binary artifact in the local Maven repository" {
            val root = tempdir()
            val folder = root.createRepositoryFolder(repoPath = "binary")
            val jar = folder.createJar("$TEST_ARTIFACT_ID-$TEST_VERSION", null)

            val helper = LocalRepositoryHelper(root)
            helper.fileForOsgiArtifact(testArtifact, isBinary = true) shouldBe jar
        }

        "return null if the folder for the artifact does not exist" {
            val root = tempdir()
            val helper = LocalRepositoryHelper(root)

            helper.fileForOsgiArtifact(testArtifact) shouldBe null
        }

        "return null if the artifact file does not exist" {
            val root = tempdir()
            root.createRepositoryFolder()

            val helper = LocalRepositoryHelper(root)
            helper.fileForOsgiArtifact(testArtifact) shouldBe null
        }
    }

    "osgiManifest()" should {
        "return the OSGi manifest for the given artifact" {
            val root = tempdir()
            val folder = root.createRepositoryFolder()
            val manifestEntries = mapOf("Bundle-SymbolicName" to "test.bundle", "foo" to "bar")
            folder.createJar("$TEST_ARTIFACT_ID-$TEST_VERSION", manifestEntries)

            val helper = LocalRepositoryHelper(root)
            val manifest = helper.osgiManifest(testArtifact)

            manifestEntries.entries.forAll { e ->
                manifest?.mainAttributes?.getValue(e.key) shouldBe e.value
            }
        }

        "return the OSGi manifest for a binary artifact" {
            val root = tempdir()
            val folder = root.createRepositoryFolder(repoPath = "binary")
            val manifestEntries = mapOf("Bundle-SymbolicName" to "test.bundle", "foo" to "bar")
            folder.createJar("$TEST_ARTIFACT_ID-$TEST_VERSION", manifestEntries)

            val helper = LocalRepositoryHelper(root)
            val manifest = helper.osgiManifest(testArtifact, isBinary = true)

            manifestEntries.entries.forAll { e ->
                manifest?.mainAttributes?.getValue(e.key) shouldBe e.value
            }
        }

        "return an empty manifest if the artifact jar does not contain a manifest" {
            val root = tempdir()
            val folder = root.createRepositoryFolder()
            folder.createJar("$TEST_ARTIFACT_ID-$TEST_VERSION", null)

            val helper = LocalRepositoryHelper(root)
            val manifest = helper.osgiManifest(testArtifact)

            manifest?.mainAttributes.shouldNotBeNull {
                this should beEmpty()
            }
        }

        "return null if the artifact file does not exist" {
            val root = tempdir()
            root.createRepositoryFolder()

            val helper = LocalRepositoryHelper(root)
            val manifest = helper.osgiManifest(testArtifact)

            manifest should beNull()
        }
    }
})

/** ID of a test bundle serving as a dependency. */
private const val TEST_ARTIFACT_ID = "org.ossreviewtoolkit.test.bundle"

/** The version number of the test dependency. */
private const val TEST_VERSION = "50.1.2"

/** A test artifact to be resolved. */
private val testArtifact = DefaultArtifact("someGroup", TEST_ARTIFACT_ID, "jar", TEST_VERSION)

/**
 * Create the folder in which to store data for the given [artifactId] and [version] if this [File] was the root of
 * a local repository. Optionally, support an alternative [repoPath] for special artifact types.
 */
private fun File.createRepositoryFolder(
    artifactId: String = TEST_ARTIFACT_ID,
    version: String = TEST_VERSION,
    repoPath: String = "osgi/bundle"
): File = resolve("p2/$repoPath/$artifactId/$version").apply { mkdirs() }

/**
 * Generate a jar file with the given [name] and [attributes] for the manifest in this folder. If [attributes] is
 * *null*, the jar will be created without a manifest.
 */
private fun File.createJar(name: String, attributes: Map<String, String>?): File {
    val jarFile = resolve("$name.jar")
    val manifest = attributes?.let { attributesMap ->
        Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            attributesMap.forEach { (key, value) -> mainAttributes.putValue(key, value) }
        }
    }

    val jarStream = manifest?.let { JarOutputStream(jarFile.outputStream(), it) }
        ?: JarOutputStream(jarFile.outputStream())

    jarStream.use { out ->
        val entry = JarEntry("foo.class")
        out.putNextEntry(entry)
        out.write("someTestData".toByteArray())
        out.closeEntry()
    }

    return jarFile
}
