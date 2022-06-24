/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.utils.common.Os

typealias AuthenticatedProxy = Pair<Proxy, PasswordAuthentication?>
typealias ProtocolProxyMap = Map<String, List<AuthenticatedProxy>>

const val DEFAULT_PROXY_PORT = 8080

/**
 * A proxy selector which supports dynamic addition and removal of proxies with optional password authentication.
 */
class OrtProxySelector(private val fallback: ProxySelector? = null) : ProxySelector() {
    companion object {
        internal val NO_PROXY_LIST = listOf(Proxy.NO_PROXY)

        /**
         * Install this proxy selector as the global default. The previous default selector is used as a fallback.
         */
        @Synchronized
        fun install(): OrtProxySelector {
            val current = getDefault()
            return if (current is OrtProxySelector) {
                logOnce(Level.INFO) { "Proxy selector is already installed." }
                current
            } else {
                OrtProxySelector(current).also {
                    setDefault(it)
                    log.info { "Proxy selector was successfully installed." }
                }
            }
        }

        /**
         * Uninstall this proxy selector, restoring the previous default selector as the global default.
         */
        @Synchronized
        fun uninstall(): ProxySelector? {
            val current = getDefault()
            return if (current is OrtProxySelector) {
                current.fallback.also {
                    setDefault(it)
                    log.info { "Proxy selector was successfully uninstalled." }
                }
            } else {
                log.info { "Proxy selector is not installed." }
                current
            }
        }
    }

    private val proxyAuthentication = mutableMapOf<Proxy, PasswordAuthentication?>()
    private val proxyOrigins = mutableMapOf<String, MutableMap<String, MutableList<Proxy>>>()

    private val proxyIncludes = listOfNotNull(
        Os.env["only_proxy"],
        System.getProperty("http.proxyIncludes"),
        System.getProperty("https.proxyIncludes")
    ).flatMapTo(mutableListOf()) { list -> list.split(',').map { it.trim() } }

    private val proxyExcludes = listOfNotNull(
        Os.env["no_proxy"],
        System.getProperty("http.proxyExcludes"),
        System.getProperty("https.proxyExcludes")
    ).flatMapTo(mutableListOf()) { list -> list.split(',').map { it.trim() } }

    init {
        determineProxyFromProperties("http")?.let {
            addProxy("properties", "http", it)
        }

        determineProxyFromProperties("https")?.let {
            addProxy("properties", "https", it)
        }

        determineProxyFromURL(Os.env["http_proxy"])?.let {
            addProxy("environment", "http", it)
        }

        determineProxyFromURL(Os.env["https_proxy"])?.let {
            addProxy("environment", "https", it)
        }
    }

    /**
     * Add a [proxy with optional password authentication][authenticatedProxy] that can handle the [protocol]. The
     * [origin] is a string that helps to identify where a proxy definition comes from.
     */
    fun addProxy(origin: String, protocol: String, authenticatedProxy: AuthenticatedProxy) =
        apply {
            val (proxy, authentication) = authenticatedProxy
            proxyAuthentication[proxy] = authentication

            val proxiesForOrigin = proxyOrigins.getOrPut(origin) { mutableMapOf() }
            val proxiesForProtocol = proxiesForOrigin.getOrPut(protocol) { mutableListOf() }
            proxiesForProtocol += proxy
        }

    /**
     * Add multiple [proxies for specific protocols][proxyMap] whose definitions come from [origin].
     */
    fun addProxies(origin: String, proxyMap: ProtocolProxyMap) =
        apply {
            proxyMap.forEach { (protocol, proxies) ->
                proxies.forEach { proxy ->
                    addProxy(origin, protocol, proxy)
                }
            }
        }

    /**
     * Remove any previously added proxies.
     */
    fun removeAllProxies() =
        apply {
            proxyAuthentication.clear()
            proxyOrigins.clear()
        }

    /**
     * Return whether the [proxy] has been defined from some origin.
     */
    fun hasOrigin(proxy: Proxy) =
        proxy in proxyOrigins.flatMap { (_, proxiesForProtocol) ->
            proxiesForProtocol.values
        }.flatten()

    /**
     * Remove all proxies whose definition comes from the [origin].
     */
    fun removeProxyOrigin(origin: String) {
        val removed = proxyOrigins.remove(origin)

        // If a removed proxy is used nowhere else, also remove its authentication.
        removed?.forEach { (_, proxies) ->
            proxies.forEach { proxy ->
                if (!hasOrigin(proxy)) {
                    proxyAuthentication.remove(proxy)
                }
            }
        }
    }

    /**
     * Return the [password authentication][PasswordAuthentication] for the [proxy], or null if no authentication is
     * known.
     */
    fun getProxyAuthentication(proxy: Proxy) = proxyAuthentication[proxy]

    override fun select(uri: URI?): List<Proxy> {
        requireNotNull(uri)

        fun URI.matches(suffix: String) = suffix.isNotEmpty() && (authority.endsWith(suffix) || host.endsWith(suffix))

        // An empty list of proxy includes means there are no restrictions as to which hosts proxies apply.
        if (proxyIncludes.isNotEmpty() && proxyIncludes.none { uri.matches(it) }) return NO_PROXY_LIST

        if (proxyExcludes.any { uri.matches(it) }) return NO_PROXY_LIST

        val proxies = proxyOrigins.flatMap { (_, proxiesForProtocol) ->
            proxiesForProtocol.getOrDefault(uri.scheme, mutableListOf())
        }

        // Quote from the upstream documentation for select: When no proxy is available, the list will contain one
        // element of type Proxy that represents a direct connection.
        return proxies.takeUnless { it.isEmpty() } ?: fallback?.select(uri) ?: NO_PROXY_LIST
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        fallback?.connectFailed(uri, sa, ioe)
    }
}

/**
 * Determine settings for an authenticated proxy for the given [protocol] from commonly set system properties.
 */
fun determineProxyFromProperties(protocol: String?): AuthenticatedProxy? {
    val type = protocol?.toProxyType() ?: return null

    val host = System.getProperty("$protocol.proxyHost") ?: return null
    val port = runCatching {
        System.getProperty("$protocol.proxyPort")?.toInt()
    }.getOrNull() ?: DEFAULT_PROXY_PORT

    val proxy = Proxy(type, InetSocketAddress(host, port))

    val authentication = System.getProperty("$protocol.proxyUser")?.let { username ->
        val password = System.getProperty("$protocol.proxyPassword").orEmpty()
        PasswordAuthentication(username, password.toCharArray())
    }

    return proxy to authentication
}

/**
 * Return a [proxy][Proxy] with optional [password authentication][PasswordAuthentication] as encoded in the [url], or
 * null if the string does not represent a URL with a supported [proxy type][Proxy.Type].
 */
fun determineProxyFromURL(url: String?): AuthenticatedProxy? {
    if (url == null) return null

    val uri = runCatching {
        // Assume http if no protocol is specified to be able to create a URI.
        URI(url.takeIf { "://" in it } ?: "http://$url")
    }.getOrElse {
        return null
    }

    val type = uri.scheme.toProxyType() ?: return null

    val authentication = uri.userInfo?.let { userInfo ->
        val username = userInfo.substringBefore(':')
        val password = userInfo.substringAfter(':', "")
        PasswordAuthentication(username, password.toCharArray())
    }

    val port = uri.port.takeIf { it in IntRange(0, 65535) } ?: DEFAULT_PROXY_PORT
    val proxy = Proxy(type, InetSocketAddress(uri.host, port))

    return proxy to authentication
}

/**
 * Return the [proxy type][Proxy.Type] encoded in the string, or null if the string does not represent a supported proxy
 * type.
 */
fun String.toProxyType() =
    when {
        startsWith("http") -> Proxy.Type.HTTP
        startsWith("socks") -> Proxy.Type.SOCKS
        else -> null
    }
