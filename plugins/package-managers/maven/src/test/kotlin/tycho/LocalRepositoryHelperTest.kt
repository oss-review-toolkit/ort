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
import io.kotest.matchers.maps.shouldContainExactly
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

    "p2Properties()" should {
        "return null if the artifact cannot be resolved" {
            val root = tempdir()

            val helper = LocalRepositoryHelper(root)
            helper.p2Properties(testArtifact) should beNull()
        }

        "return an empty map if the XML file does not exist" {
            val root = tempdir()
            val folder = root.createRepositoryFolder()
            folder.createJar("$TEST_ARTIFACT_ID-$TEST_VERSION", null)

            val helper = LocalRepositoryHelper(root)
            helper.p2Properties(testArtifact).shouldNotBeNull {
                this should beEmpty()
            }
        }

        "return an empty map if the XML file is not valid" {
            val root = tempdir()
            val folder = root.createRepositoryFolder()
            folder.createJar("$TEST_ARTIFACT_ID-$TEST_VERSION", null)
            val xmlFile = folder.resolve("$TEST_ARTIFACT_ID-$TEST_VERSION-p2artifacts.xml")
            xmlFile.writeText("invalid XML")

            val helper = LocalRepositoryHelper(root)
            helper.p2Properties(testArtifact).shouldNotBeNull {
                this should beEmpty()
            }
        }

        "return the properties extracted from the XML file" {
            val root = tempdir()
            val artifact = DefaultArtifact("someGroup", "org.objectweb.asm", "jar", "9.7.0")
            val folder = root.createRepositoryFolder(artifact.artifactId, artifact.version)

            val srcFile = File("src/test/assets/p2artifacts.xml")
            val xmlFile = folder.resolve("${artifact.artifactId}-${artifact.version}-p2artifacts.xml")
            srcFile.copyTo(xmlFile)

            val expectedProperties = mapOf(
                "maven-groupId" to "org.ow2.asm",
                "maven-artifactId" to "asm",
                "maven-version" to "9.7",
                "maven-repository" to "eclipse.maven.central.mirror",
                "maven-type" to "jar",
                "download.size" to "125428",
                "artifact.size" to "125428",
                "download.checksum.sha-512" to "ada37fcc95884a4d2cbc64495f5f67556c847e7724e26ccfbb15cc42a476436fa54b" +
                    "5d4fd4d9ed340241d848999d415e1cff07045d9e97d451c16aeed4911045",
                "download.checksum.sha-256" to "adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d",
                "download.checksum.sha-1" to "073d7b3086e14beb604ced229c302feff6449723"
            )

            val helper = LocalRepositoryHelper(root)
            helper.p2Properties(artifact).shouldNotBeNull {
                this shouldContainExactly expectedProperties
            }
        }

        "return only the properties for the correct artifact" {
            val root = tempdir()
            val folder = root.createRepositoryFolder()
            folder.createJar("$TEST_ARTIFACT_ID-$TEST_VERSION", null)
            val xmlFile = folder.resolve("$TEST_ARTIFACT_ID-$TEST_VERSION-p2artifacts.xml")
            xmlFile.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <artifacts size='1'>
                    <artifact classifier='osgi.bundle' id='org.objectweb.asm' version='9.6.0'>
                        <properties size='5'>
                            <property name='maven-groupId' value='org.ow2.asm'/>
                            <property name='maven-artifactId' value='asm'/>
                            <property name='maven-version' value='9.7'/>
                            <property name='maven-repository' value='eclipse.maven.central.mirror'/>
                            <property name='maven-type' value='jar'/>
                        </properties>
                    </artifact>
                </artifacts>
                """.trimIndent()
            )

            val helper = LocalRepositoryHelper(root)
            helper.p2Properties(testArtifact).shouldNotBeNull {
                this should beEmpty()
            }
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
 * a local repository.
 */
private fun File.createRepositoryFolder(artifactId: String = TEST_ARTIFACT_ID, version: String = TEST_VERSION): File =
    resolve("p2/osgi/bundle/$artifactId/$version").apply { mkdirs() }

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
