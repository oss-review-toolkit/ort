/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.utils

import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector

import org.apache.logging.log4j.Level

/**
 * An authenticator for network connections established by ORT. For proxy authentication, the [OrtProxySelector] is
 * required to also be installed.
 */
class OrtAuthenticator(private val original: Authenticator? = null) : Authenticator() {
    companion object {
        /**
         * Install this authenticator as the global default.
         */
        @Synchronized
        fun install(): OrtAuthenticator {
            val current = getDefault()
            return if (current is OrtAuthenticator) {
                logOnce(Level.INFO) { "Authenticator is already installed." }
                current
            } else {
                OrtAuthenticator(current).also {
                    setDefault(it)
                    log.info { "Authenticator was successfully installed." }
                }
            }
        }

        /**
         * Uninstall this authenticator, restoring the previous authenticator as the global default.
         */
        @Synchronized
        fun uninstall(): Authenticator? {
            val current = getDefault()
            return if (current is OrtAuthenticator) {
                current.original.also {
                    setDefault(it)
                    log.info { "Authenticator was successfully uninstalled." }
                }
            } else {
                log.info { "Authenticator is not installed." }
                current
            }
        }
    }

    // TODO: Add support for ".authinfo" files (which use the same syntax as .netrc files) once Git.kt does not call the
    //       Git CLI anymore which only supports ".netrc" (and "_netrc") files.
    private val netrcFileNames = listOf(".netrc", "_netrc")

    private val serverAuthentication = mutableMapOf<String, PasswordAuthentication>()

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        when (requestorType) {
            RequestorType.PROXY -> {
                val proxySelector = ProxySelector.getDefault()
                if (proxySelector is OrtProxySelector) {
                    val type = requestingProtocol.toProxyType() ?: return super.getPasswordAuthentication()
                    val proxy = Proxy(type, InetSocketAddress(requestingHost, requestingPort))
                    return proxySelector.getProxyAuthentication(proxy)
                }
            }

            RequestorType.SERVER -> {
                serverAuthentication[requestingHost]?.let { return it }

                // First look for (potentially machine-specific) credentials in a netrc-style file.
                netrcFileNames.forEach { name ->
                    val netrcFile = Os.userHomeDirectory.resolve(name)
                    if (netrcFile.isFile) {
                        log.debug { "Parsing '$netrcFile' for machine '$requestingHost'." }

                        getNetrcAuthentication(netrcFile.readText(), requestingHost)?.let {
                            serverAuthentication[requestingHost] = it
                            return it
                        }
                    }
                }

                // Then look for generic credentials passed as environment variables.
                val usernameFromEnv = Os.env["ORT_HTTP_USERNAME"]
                val passwordFromEnv = Os.env["ORT_HTTP_PASSWORD"]
                if (usernameFromEnv != null && passwordFromEnv != null) {
                    return PasswordAuthentication(usernameFromEnv, passwordFromEnv.toCharArray())
                }
            }

            null -> log.warn { "No requestor type set for password authentication." }
        }

        return super.getPasswordAuthentication()
    }
}

/**
 * Parse the [contents] of a [.netrc](https://www.gnu.org/software/inetutils/manual/html_node/The-_002enetrc-file.html)
 * file for a login / password matching [machine].
 */
fun getNetrcAuthentication(contents: String, machine: String): PasswordAuthentication? {
    val lines = contents.lines().mapNotNull { line ->
        line.trim().takeUnless { it.startsWith('#') }
    }

    val iterator = lines.joinToString(" ").split(Regex("\\s+")).iterator()

    var machineFound = false
    var login: String? = null
    var password: String? = null

    while (iterator.hasNext()) {
        when (iterator.next()) {
            "machine" -> machineFound = iterator.hasNext() && iterator.next() == machine
            "login" -> login = if (machineFound && iterator.hasNext()) iterator.next() else null
            "password" -> password = if (machineFound && iterator.hasNext()) iterator.next() else null
            "default" -> machineFound = true
        }

        if (login != null && password != null) return PasswordAuthentication(login, password.toCharArray())
    }

    return null
}
