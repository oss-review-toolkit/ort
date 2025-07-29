/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.composer

import java.io.File

import kotlin.io.path.moveTo

import org.ossreviewtoolkit.utils.common.div

import org.semver4j.Semver

private const val COMPOSER_LOCK_FILE = "composer.lock"

class LockfileProvider(private val definitionFile: File) {
    private val workingDir = definitionFile.parentFile

    val lockfile = workingDir / COMPOSER_LOCK_FILE

    fun <T> ensureLockfile(block: (File) -> T): T {
        if (lockfile.isFile) return block(lockfile)

        val definitionFileBackup = enableLockfileCreation()

        return try {
            require(createLockFile())
            block(lockfile)
        } finally {
            lockfile.delete()
            definitionFileBackup?.toPath()?.moveTo(definitionFile.toPath(), overwrite = true)
        }
    }

    private fun enableLockfileCreation(): File? {
        var definitionFileBackup: File? = null
        val lockConfig = ComposerCommand.run(workingDir, "--no-interaction", "config", "lock")

        if (lockConfig.isSuccess && lockConfig.stdout.trim() == "false") {
            File.createTempFile("composer", "json", workingDir).also {
                // The above call already creates an empty file, so the copy call needs to overwrite it.
                definitionFileBackup = definitionFile.copyTo(it, overwrite = true)
            }

            // Ensure that the build is not configured to disallow the creation of lockfiles.
            val unsetLock = ComposerCommand.run(workingDir, "--no-interaction", "config", "--unset", "lock")
            if (unsetLock.isError) {
                definitionFileBackup?.delete()
                return null
            }
        }

        return definitionFileBackup
    }

    private fun createLockFile(): Boolean {
        val args = buildList {
            add("--no-interaction")
            add("update")
            add("--ignore-platform-reqs")

            val composerVersion = Semver(ComposerCommand.getVersion(workingDir))
            if (composerVersion.major >= 2) {
                add("--no-install")
                add("--no-audit")
            }
        }

        val update = ComposerCommand.run(workingDir, *args.toTypedArray())
        return update.isSuccess
    }
}
