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

package org.ossreviewtoolkit.utils.core

import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.ConcurrentHashMap

import org.apache.logging.log4j.Level

/**
 * A caching authenticator that chains other authenticators. For proxy authentication, the [OrtProxySelector] is
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

    // First look for (potentially machine-specific) credentials in a netrc-style file, then look for generic
    // credentials passed as environment variables.
    private val delegateAuthenticators = listOf(NetRcAuthenticator(), EnvVarAuthenticator())

    private val serverAuthentication: ConcurrentHashMap<String, PasswordAuthentication> = ConcurrentHashMap()

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

                delegateAuthenticators.forEach { authenticator ->
                    authenticator.requestPasswordAuthenticationInstance(
                        requestingHost,
                        requestingSite,
                        requestingPort,
                        requestingProtocol,
                        requestingPrompt,
                        requestingScheme,
                        requestingURL,
                        requestorType
                    )?.let {
                        serverAuthentication[requestingHost] = it
                        return it
                    }
                }
            }

            null -> log.warn { "No requestor type set for password authentication." }
        }

        return super.getPasswordAuthentication()
    }
}
