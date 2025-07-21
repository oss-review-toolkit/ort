/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.ConcurrentHashMap

import org.apache.logging.log4j.kotlin.logger

/**
 * A caching authenticator that chains other authenticators. For proxy authentication, the [OrtProxySelector] is
 * required to also be installed.
 */
open class OrtAuthenticator(
    private val original: Authenticator? = null,

    /** A flag whether resolved authentication credentials should be cached. */
    private val cacheAuthentication: Boolean = true
) : Authenticator() {
    companion object {
        /**
         * Install this authenticator as the global default.
         */
        @Synchronized
        fun install(): OrtAuthenticator {
            val current = getDefault()
            return if (current is OrtAuthenticator) {
                current
            } else {
                OrtAuthenticator(current).also {
                    setDefault(it)
                    logger.info { "Authenticator was successfully installed." }
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
                    logger.info { "Authenticator was successfully uninstalled." }
                }
            } else {
                logger.info { "Authenticator is not installed." }
                current
            }
        }
    }

    // First look if the credentials are already present in the URL, then search for (potentially machine-specific)
    // credentials in a netrc-style file, and finally look for generic credentials passed as environment variables.
    // If none of these can provide credentials, fall back to the original authenticator, if any. This allows tools
    // that use ORT programmatically to provide a custom authenticator.
    protected open val delegateAuthenticators = listOfNotNull(
        UserInfoAuthenticator(),
        NetRcAuthenticator(),
        EnvVarAuthenticator(),
        original
    )

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
                        if (cacheAuthentication) {
                            serverAuthentication[requestingHost] = it
                        }

                        return it
                    }
                }
            }

            null -> logger.warn { "No requestor type set for password authentication." }
        }

        return super.getPasswordAuthentication()
    }
}
