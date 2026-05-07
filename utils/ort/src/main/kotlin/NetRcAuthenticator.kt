/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.ort

import java.io.File
import java.lang.invoke.MethodHandles
import java.net.Authenticator
import java.net.PasswordAuthentication

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.nextOrNull

/**
 * A simple authenticator that reads credentials from .netrc-style files. The files are loaded when an instance is
 * created, and the credentials they contain are extracted. This information is then used to answer authentication
 * requests.
 */
class NetRcAuthenticator(
    /**
     * A list with .netrc-style files to be processed by this instance. On instantiation, the class reads these
     * files (in this order) and extracts the credentials they contain. If there are multiple entries for the same
     * machine in different files, the last file wins.
     */
    netrcFiles: List<File> = DEFAULT_NETRC_FILENAMES.map<String, File> { Os.userHomeDirectory / it }
) : Authenticator() {
    companion object {
        // TODO: Add support for ".authinfo" files (which use the same syntax as .netrc files) once Git.kt does not
        //       call the Git CLI anymore which only supports ".netrc" (and "_netrc") files.
        val DEFAULT_NETRC_FILENAMES = listOf(".netrc", "_netrc")
    }

    private val credentials = loadCredentials(netrcFiles)

    override fun getPasswordAuthentication(): PasswordAuthentication? =
        credentials[requestingHost] ?: credentials["default"] ?: super.getPasswordAuthentication()
}

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * Parse the [contents] of a [.netrc](https://www.gnu.org/software/inetutils/manual/html_node/The-_002enetrc-file.html)
 * and extract a [Map] with the credentials using the machine names as keys.
 */
internal fun parseNetrc(contents: String): Map<String, PasswordAuthentication> {
    val lines = contents.lines().mapNotNull { line ->
        line.trim().takeUnless { it.startsWith('#') }
    }

    val iterator = lines.joinToString(" ").split(Regex("\\s+")).iterator()

    val credentials = mutableMapOf<String, PasswordAuthentication>()
    var machine: String? = null
    var login: String? = null
    var password: String? = null

    while (iterator.hasNext()) {
        when (val token = iterator.next()) {
            "machine" -> {
                machine = iterator.nextOrNull()
                login = null
                password = null
            }

            "login" -> login = machine?.let { iterator.nextOrNull() }

            "password" -> password = machine?.let { iterator.nextOrNull() }

            "default" -> machine = token
        }

        if (machine != null && login != null && password != null) {
            logger.debug { "Found a '$machine' entry." }
            credentials[machine] = PasswordAuthentication(login, password.toCharArray())
        }
    }

    return credentials
}

/**
 * Parse the given [netrcFiles] return a [Map] with the found credentials.
 */
private fun loadCredentials(netrcFiles: List<File>): Map<String, PasswordAuthentication> =
    netrcFiles.fold(emptyMap()) { credentials, netrcFile ->
        if (netrcFile.isFile) {
            logger.debug { "Parsing '$netrcFile'." }

            val netrcText = netrcFile.readText()

            credentials + parseNetrc(netrcText)
        } else {
            logger.debug { "No netrc file found at '$netrcFile'." }
            credentials
        }
    }
