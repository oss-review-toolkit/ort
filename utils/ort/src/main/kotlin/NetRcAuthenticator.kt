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
    /** The map with known credentials keyed by host names. */
    private val credentials: Map<String, PasswordAuthentication> = loadCredentials()
) : Authenticator() {
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
    var machineFound: String? = null
    var login: String? = null
    var password: String? = null

    while (iterator.hasNext()) {
        when (val token = iterator.next()) {
            "machine" -> {
                machineFound = iterator.nextOrNull()
                login = null
                password = null
            }

            "login" -> login = machineFound?.let { iterator.nextOrNull() }

            "password" -> password = machineFound?.let { iterator.nextOrNull() }

            "default" -> machineFound = token
        }

        if (login != null && password != null && machineFound != null) {
            logger.debug { "Found a '$machineFound' entry." }
            credentials[machineFound] = PasswordAuthentication(login, password.toCharArray())
        }
    }

    return credentials
}

/**
 * Parse all available files with credentials and return a [Map] with the found credentials.
 */
private fun loadCredentials(): Map<String, PasswordAuthentication> {
    // TODO: Add support for ".authinfo" files (which use the same syntax as .netrc files) once Git.kt does not call the
    //       Git CLI anymore which only supports ".netrc" (and "_netrc") files.
    val netrcFileNames = listOf(".netrc", "_netrc")

    return netrcFileNames.fold(emptyMap()) { credentials, name ->
        val netrcFile = Os.userHomeDirectory / name
        if (netrcFile.isFile) {
            logger.debug { "Parsing '$netrcFile'." }

            val netrcText = netrcFile.readText()

            credentials + parseNetrc(netrcText)
        } else {
            logger.debug { "No netrc file found at '$netrcFile'." }
            credentials
        }
    }
}
