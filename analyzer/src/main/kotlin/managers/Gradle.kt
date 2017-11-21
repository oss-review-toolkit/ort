/*
 * Copyright (c) 2017 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer.managers

import Dependency
import DependencyTreeModel

import ch.frankel.slf4k.*

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.Scope
import com.here.ort.util.OS
import com.here.ort.util.log

import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnector

import java.io.File

class Gradle : PackageManager() {
    companion object : PackageManagerFactory<Gradle>(
            "https://gradle.org/",
            "Java",
            listOf("build.gradle")
    ) {
        override fun create() = Gradle()
    }

    val gradle: String
    val wrapper: String

    init {
        if (OS.isWindows) {
            gradle = "gradle.bat"
            wrapper = "gradlew.bat"
        } else {
            gradle = "gradle"
            wrapper = "gradlew"
        }
    }

    override fun command(workingDir: File): String {
        return if (File(workingDir, wrapper).isFile) wrapper else gradle
    }

    override fun resolveDependencies(projectDir: File, workingDir: File, definitionFile: File): AnalyzerResult? {
        val connection = GradleConnector
                .newConnector()
                .forProjectDirectory(workingDir)
                .connect()

        try {
            val initScriptFile = File.createTempFile("init", "gradle")
            initScriptFile.writeBytes(javaClass.classLoader.getResource("init.gradle").readBytes())

            val dependencyTreeModel = connection
                    .model(DependencyTreeModel::class.java)
                    .withArguments("--init-script", initScriptFile.absolutePath)
                    .get()

            if (!initScriptFile.delete()) {
                log.warn { "Init script file '${initScriptFile.absolutePath}' could not be deleted." }
            }

            val packages = mutableMapOf<String, Package>()
            val scopes = dependencyTreeModel.configurations.map { configuration ->
                val dependencies = configuration.dependencies.map { dependency ->
                    parseDependency(dependency, packages)
                }

                Scope(configuration.name, true, dependencies.toSortedSet())
            }

            val vcs = VersionControlSystem.fromDirectory(projectDir)
            val project = Project(
                    packageManager = javaClass.simpleName,
                    namespace = "",
                    name = dependencyTreeModel.name,
                    version = "",
                    declaredLicenses = sortedSetOf(),
                    aliases = emptyList(),
                    vcsProvider = vcs?.toString() ?: "",
                    vcsUrl = vcs?.getRemoteUrl(projectDir) ?: "",
                    vcsRevision = vcs?.getWorkingRevision(projectDir) ?: "",
                    vcsPath = vcs?.getPathToRoot(projectDir) ?: "",
                    homepageUrl = "",
                    scopes = scopes.toSortedSet()
            )

            return AnalyzerResult(true, project, packages.values.toSortedSet())
        } catch (e: BuildException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not analyze '${definitionFile.absolutePath}': ${e.message}" }
            return null
        } finally {
            connection.close()
        }
    }

    private fun parseDependency(dependency: Dependency, packages: MutableMap<String, Package>): PackageReference {
        if (dependency.error == null) {
            // Only look for a package when there was no error resolving the dependency.
            packages.getOrPut("${dependency.groupId}:${dependency.artifactId}:${dependency.version}") {
                // TODO: add metadata for package
                Package(
                        packageManager = javaClass.simpleName,
                        namespace = dependency.groupId,
                        name = dependency.artifactId,
                        version = dependency.version,
                        declaredLicenses = sortedSetOf(),
                        description = "",
                        homepageUrl = "",
                        downloadUrl = "",
                        hash = "",
                        hashAlgorithm = "",
                        vcsProvider = "",
                        vcsUrl = "",
                        vcsRevision = "",
                        vcsPath = ""
                )
            }
        }
        val transitiveDependencies = dependency.dependencies.map { parseDependency(it, packages) }
        return PackageReference(dependency.groupId, dependency.artifactId, dependency.version,
                transitiveDependencies.toSortedSet(), dependency.error?.let { listOf(it) } ?: emptyList())
    }

}
