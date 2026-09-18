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

package org.ossreviewtoolkit.plugins.packagemanagers.platformio

import java.io.File

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.ossreviewtoolkit.utils.common.div

private const val PLATFORM_MANIFEST_FILE = "platform.json"
private const val PACKAGE_MANIFEST_FILE = "package.json"

// Matches the root line of the "Platform" tree printed by `pio pkg list --only-platforms`, e.g.:
// Platform atmelavr @ 5.3.0 (required: atmelavr)
private val PLATFORM_ROOT_REGEX = Regex("""^Platform (\S+) @ (\S+)""")

// Matches a child line of the "Platform" tree printed by `pio pkg list --only-platforms`, e.g.:
// ├── framework-arduino-avr @ 5.4.0 (required: platformio/framework-arduino-avr @ ~5.4.0)
private val PLATFORM_CHILD_REGEX = Regex("""^(?:├──|└──) (\S+) @ (\S+)""")

/**
 * Return the PlatformIO core directory (by default "~/.platformio").
 */
internal fun getCoreDir(workingDir: File): File {
    val output = PlatformIoCommand.run(workingDir, "system", "info", "--json-output").requireSuccess().stdout
    val coreDir = JSON.parseToJsonElement(output).jsonObject["core_dir"]?.jsonObject
        ?.get("value")?.jsonPrimitive?.content

    return File(requireNotNull(coreDir) { "Could not determine the PlatformIO core directory." })
}

internal data class ResolvedPackageRef(val name: String, val version: String)

internal fun readPlatform(
    workingDir: File,
    environmentName: String,
    coreDir: File,
    onIssue: (String) -> Unit
): PlatformIoPackage? {
    val output = PlatformIoCommand.run(
        workingDir, "pkg", "list", "-e", environmentName, "--only-platforms"
    ).requireSuccess().stdout

    val (root, children) = parsePlatformTree(output) ?: return null

    val platformDir = findPackageDir(coreDir / "platforms", root) ?: run {
        onIssue("Could not find the installation directory for platform '${root.name}@${root.version}'.")
        return null
    }

    val platform = readResolvedPackage(platformDir, PLATFORM_MANIFEST_FILE) ?: run {
        onIssue("Could not read the manifest for platform '${root.name}@${root.version}'.")
        return null
    }

    val platformManifestFile = platformDir / PLATFORM_MANIFEST_FILE
    val frameworkPackageNames = platformManifestFile.takeIf { it.isFile }
        ?.let { parsePlatformPackageNamesByType(it.readText(), "framework") }
        .orEmpty()

    val nonFrameworkChildren = children.filterNot { it.name in frameworkPackageNames }.mapNotNull { child ->
        val dir = findPackageDir(coreDir / "packages", child) ?: run {
            onIssue("Could not find the installation directory for package '${child.name}@${child.version}'.")
            return@mapNotNull null
        }

        readResolvedPackage(dir, PACKAGE_MANIFEST_FILE) ?: run {
            onIssue("Could not read the manifest for package '${child.name}@${child.version}'.")
            null
        }
    }

    // The "pio project metadata" call is expensive and unnecessary if no framework packages need disambiguation.
    val usedFrameworkDirs = if (frameworkPackageNames.isEmpty()) {
        emptyList()
    } else {
        findUsedPackageDirs(workingDir, environmentName, coreDir)
            .filter { it.name.substringBefore('@') in frameworkPackageNames }
    }

    val frameworkChildren = usedFrameworkDirs.mapNotNull { dir ->
        readResolvedPackage(dir, PACKAGE_MANIFEST_FILE) ?: run {
            onIssue("Could not read the manifest for package installed at '${dir.path}'.")
            null
        }
    }

    platform.dependencies = nonFrameworkChildren + frameworkChildren

    return platform
}

internal fun parsePlatformPackageNamesByType(json: String, type: String): Set<String> {
    val packages = JSON.parseToJsonElement(json).jsonObject["packages"]?.jsonObject ?: return emptySet()

    return packages.filterValues { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == type }.keys
}

private fun findUsedPackageDirs(workingDir: File, environmentName: String, coreDir: File): List<File> {
    val output = PlatformIoCommand.run(
        workingDir, "project", "metadata", "-e", environmentName, "--json-output"
    ).requireSuccess().stdout

    val packagesDir = coreDir / "packages"

    // Normalize to forward slashes on both sides, as PlatformIO (a Python tool) is not guaranteed to emit paths
    // using the current OS' separator, e.g. it may emit POSIX-style paths even when running on Windows.
    val prefix = "${packagesDir.path.replace('\\', '/')}/"

    val dirNames = mutableSetOf<String>()
    collectStringValues(JSON.parseToJsonElement(output)) { value ->
        val normalizedValue = value.replace('\\', '/')
        if (normalizedValue.startsWith(prefix)) dirNames += normalizedValue.removePrefix(prefix).substringBefore('/')
    }

    return dirNames.map { packagesDir / it }
}

private fun collectStringValues(element: JsonElement, action: (String) -> Unit) {
    when (element) {
        is JsonObject -> element.values.forEach { collectStringValues(it, action) }
        is JsonArray -> element.forEach { collectStringValues(it, action) }
        is JsonPrimitive -> element.contentOrNull?.let(action)
    }
}

/**
 * Parse the tree printed by `pio pkg list --only-platforms` for a single environment, returning the platform
 * ("root") package reference together with the references of its direct package dependencies ("children"), or null
 * if the output does not contain a platform (e.g. because the environment's platform does not manage any packages).
 */
internal fun parsePlatformTree(output: String): Pair<ResolvedPackageRef, List<ResolvedPackageRef>>? {
    val lines = output.lines()
    val rootIndex = lines.indexOfFirst { PLATFORM_ROOT_REGEX.containsMatchIn(it) }
    if (rootIndex == -1) return null

    val root = PLATFORM_ROOT_REGEX.find(lines[rootIndex])?.toResolvedPackageRef() ?: return null

    val children = lines.drop(rootIndex + 1)
        .takeWhile { it.isNotBlank() }
        .mapNotNull { PLATFORM_CHILD_REGEX.find(it)?.toResolvedPackageRef() }

    return root to children
}

private fun MatchResult.toResolvedPackageRef() = ResolvedPackageRef(groupValues[1], groupValues[2])

private fun findPackageDir(baseDir: File, ref: ResolvedPackageRef): File? {
    // Prefer the version-pinned directory if it is present, which indicates multiple versions of the same package
    // are installed side-by-side.
    val pinnedDir = baseDir / "${ref.name}@${ref.version}"
    if (pinnedDir.isDirectory) return pinnedDir

    return (baseDir / ref.name).takeIf { it.isDirectory }
}

private fun readResolvedPackage(dir: File, manifestFileName: String): PlatformIoPackage? {
    val manifestFile = dir / manifestFileName
    if (!manifestFile.isFile) return null

    val manifest = runCatching { parsePackageManifest(manifestFile.readText()) }.getOrNull() ?: return null
    val metadata = runCatching {
        (dir / PACKAGE_METADATA_FILE).takeIf { it.isFile }?.let { parsePackageMetadata(it.readText()) }
    }.getOrNull()

    return PlatformIoPackage(
        manifest = manifest,
        authors = emptySet(),
        owner = metadata?.spec?.owner,
        version = metadata?.version ?: manifest.version.orEmpty()
    )
}
