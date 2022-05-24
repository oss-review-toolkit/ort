/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import java.net.Authenticator
import java.net.PasswordAuthentication

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.nextOrNull

/**
 * A simple non-caching authenticator that reads credentials from .netrc-style files.
 */
class NetRcAuthenticator : Authenticator() {
    // TODO: Add support for ".authinfo" files (which use the same syntax as .netrc files) once Git.kt does not call the
    //       Git CLI anymore which only supports ".netrc" (and "_netrc") files.
    private val netrcFileNames = listOf(".netrc", "_netrc")

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        netrcFileNames.forEach { name ->
            val netrcFile = Os.userHomeDirectory.resolve(name)
            if (netrcFile.isFile) {
                log.debug { "Parsing '$netrcFile' for machine '$requestingHost'." }

                // Read the file on each function call to be up-to-date with any changes.
                val netrcText = netrcFile.readText()

                return getNetrcAuthentication(netrcText, requestingHost)?.let { return it }
            }
        }

        return super.getPasswordAuthentication()
    }
}

/**
 * Parse the [contents] of a [.netrc](https://www.gnu.org/software/inetutils/manual/html_node/The-_002enetrc-file.html)
 * file for a login / password matching [machine].
 */
internal fun getNetrcAuthentication(contents: String, machine: String): PasswordAuthentication? {
    val lines = contents.lines().mapNotNull { line ->
        line.trim().takeUnless { it.startsWith('#') }
    }

    val iterator = lines.joinToString(" ").split(Regex("\\s+")).iterator()

    var machineFound: String? = null
    var login: String? = null
    var password: String? = null

    while (iterator.hasNext()) {
        when (val token = iterator.next()) {
            "machine" -> machineFound = token.takeIf { iterator.nextOrNull() == machine }
            "login" -> login = machineFound?.let { iterator.nextOrNull() }
            "password" -> password = machineFound?.let { iterator.nextOrNull() }
            "default" -> machineFound = token
        }

        if (login != null && password != null) {
            OrtAuthenticator.log.debug { "Found a '$machineFound' entry for '$machine'." }
            return PasswordAuthentication(login, password.toCharArray())
        }
    }

    return null
}
