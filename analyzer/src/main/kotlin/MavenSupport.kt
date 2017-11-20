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

package com.here.ort.analyzer

import com.here.ort.util.log

import java.io.File

import org.apache.maven.artifact.repository.LegacyLocalRepositoryManager
import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.project.DefaultProjectBuildingRequest
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.project.ProjectBuildingResult
import org.apache.maven.repository.internal.MavenRepositorySystemUtils

import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.logging.BaseLoggerManager

import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.LocalRepositoryManager

fun Artifact.identifier() = "$groupId:$artifactId:$version"

class MavenSupport(localRepositoryManagerConverter: (LocalRepositoryManager) -> LocalRepositoryManager) {
    companion object {
        val SCM_REGEX = Regex("scm:[^:]+:(.+)")
    }

    val container = createContainer()
    private val repositorySystemSession = createRepositorySystemSession(localRepositoryManagerConverter)

    private fun createContainer(): PlexusContainer {
        val configuration = DefaultContainerConfiguration().apply {
            autoWiring = true
            classPathScanning = PlexusConstants.SCANNING_INDEX
            classWorld = ClassWorld("plexus.core", javaClass.classLoader)
        }

        return DefaultPlexusContainer(configuration).apply {
            loggerManager = object : BaseLoggerManager() {
                override fun createLogger(name: String) = MavenLogger(log.effectiveLevel)
            }
        }
    }

    private fun createRepositorySystemSession(
            localRepositoryManagerConverter: (LocalRepositoryManager) -> LocalRepositoryManager = { it })
            : RepositorySystemSession {
        val mavenRepositorySystem = container.lookup(MavenRepositorySystem::class.java, "default")
        val aetherRepositorySystem = container.lookup(RepositorySystem::class.java, "default")
        val repositorySystemSession = MavenRepositorySystemUtils.newSession()
        val mavenExecutionRequest = DefaultMavenExecutionRequest()
        val localRepository = mavenRepositorySystem.createLocalRepository(mavenExecutionRequest,
                org.apache.maven.repository.RepositorySystem.defaultUserLocalRepository)

        val session = LegacyLocalRepositoryManager.overlay(localRepository, repositorySystemSession,
                aetherRepositorySystem)

        val localRepositoryManager = localRepositoryManagerConverter(session.localRepositoryManager)

        return if (localRepositoryManager == session.localRepositoryManager) {
            session
        } else {
            DefaultRepositorySystemSession(session).setLocalRepositoryManager(localRepositoryManager)
        }
    }

    fun buildMavenProject(pomFile: File): ProjectBuildingResult {
        val projectBuilder = container.lookup(ProjectBuilder::class.java, "default")
        val projectBuildingRequest = createProjectBuildingRequest(true)

        return projectBuilder.build(pomFile, projectBuildingRequest)
    }

    fun createProjectBuildingRequest(resolveDependencies: Boolean) =
            DefaultProjectBuildingRequest().apply {
                isResolveDependencies = resolveDependencies
                repositorySession = repositorySystemSession
                systemProperties["java.home"] = System.getProperty("java.home")
                systemProperties["java.version"] = System.getProperty("java.version")
            }

    fun parseVcsInfo(mavenProject: MavenProject) =
            VcsInfo(parseVcsProvider(mavenProject), parseVcsUrl(mavenProject), parseVcsRevision(mavenProject))

    fun parseVcsProvider(mavenProject: MavenProject): String {
        mavenProject.scm?.connection?.split(":")?.let {
            if (it.size > 1 && it[0] == "scm") {
                return it[1]
            }
        }
        return ""
    }

    fun parseVcsUrl(mavenProject: MavenProject) =
            mavenProject.scm?.connection?.let {
                SCM_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)
            } ?: ""

    fun parseVcsRevision(mavenProject: MavenProject) = mavenProject.scm?.tag ?: ""

    data class VcsInfo(val provider: String, val url: String, val revision: String) {
        fun isEmpty() = provider.isEmpty() && url.isEmpty() && revision.isEmpty()
    }

}
