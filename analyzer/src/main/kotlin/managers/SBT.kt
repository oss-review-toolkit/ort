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

import ch.frankel.slf4k.*

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.util.OS
import com.here.ort.util.ProcessCapture
import com.here.ort.util.checkCommandVersion
import com.here.ort.util.log

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

import java.io.File

class SBT : PackageManager() {
    companion object : PackageManagerFactory<SBT>(
            "http://www.scala-sbt.org/",
            "Scala",
            listOf("build.sbt", "build.scala")
    ) {
        private val VERSION_REGEX = Regex("\\[info]\\s+(\\d+\\.\\d+\\.[^\\s]+)")
        private val POM_REGEX = Regex("\\[info] Wrote (.+\\.pom)")

        override fun create() = SBT()
    }

    override fun command(workingDir: File) = if (OS.isWindows) "sbt.bat" else "sbt"

    private fun extractLowestSbtVersion(stdout: String): String {
        val versions = stdout.lines().mapNotNull {
            VERSION_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)?.let { Semver(it) }
        }

        val uniqueVersions = versions.toSortedSet()
        if (uniqueVersions.size > 1) {
            log.info { "Different sbt versions used in the same project: $uniqueVersions" }
        }

        return uniqueVersions.first().toString()
    }

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        // We need at least sbt version 0.13.0 to be able to use "makePom" instead of the deprecated hyphenated form
        // "make-pom" and to support declaring Maven-style repositories, see
        // http://www.scala-sbt.org/0.13/docs/Publishing.html#Modifying+the+generated+POM.
         if (definitionFiles.isNotEmpty()) {
             // Note that "sbt sbtVersion" behaves differently when executed inside or outside an SBT project, see
             // https://stackoverflow.com/a/20337575/1127485.
             val workingDir = definitionFiles.first().parentFile

             // See https://github.com/sbt/sbt/issues/2695.
             var sbtLogNoformat = "\"-Dsbt.log.noformat=true\""
             if (!OS.isWindows) {
                 sbtLogNoformat = sbtLogNoformat.removeSurrounding("\"")
             }

             checkCommandVersion(
                     command(workingDir),
                     Requirement.buildIvy("[0.13.0,)"),
                     versionArguments = "$sbtLogNoformat sbtVersion",
                     workingDir = workingDir,
                     ignoreActualVersion = Main.ignoreVersions,
                     transform = this::extractLowestSbtVersion
             )
         }

        val pomFiles = sortedSetOf<File>()

        definitionFiles.forEach { definitionFile ->
            val workingDir = definitionFile.parentFile
            val sbt = ProcessCapture(workingDir, command(workingDir), "makePom").requireSuccess()

            // Get the list of POM files created by "sbt makePom". A single call might create multiple POM files in
            // case of sub-projects.
            val makePomFiles = sbt.stdout().lines().mapNotNull {
                POM_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)?.let { File(it) }
            }

            pomFiles.addAll(makePomFiles)
        }

        return pomFiles.toList()
    }

    override fun resolveDependencies(projectDir: File, definitionFiles: List<File>) =
        // Simply pass on the list of POM files to Maven, ignoring the SBT build files here.
        // TODO: Fix Maven being listed as the package manager in the result.
        Maven.create().resolveDependencies(projectDir, prepareResolution(definitionFiles))
}
