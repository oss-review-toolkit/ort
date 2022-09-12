/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners.scanoss

import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * A data class that holds the configuration options supported by the [ScanOss] scanner. An instance of this class is
 * created from the options contained in a [ScannerConfiguration] object under the key _ScanOss_. It offers the
 * following configuration options:
 */
internal data class ScanOssConfig(
    /** URL of the ScanOSS server. */
    val apiUrl: String,

    /** API Key required to authenticate with the ScanOSS server. */
    val apiKey: String,

    /** A list of suffixes of files to be ignored during the scan. */
    val ignoredFileSuffixes: List<String>
) {
    companion object {
        /** Name of the configuration property for the API URL. */
        const val API_URL_PROPERTY = "apiUrl"

        /** Name of the configuration property for the API key. */
        const val API_KEY_PROPERTY = "apiKey"

        /** Name of the configuration property for the list of ignored file suffixes. */
        const val IGNORED_FILE_SUFFIXES_PROPERTY = "ignoredFileSuffixes"

        /** The default API URL to use. */
        private const val DEFAULT_API_URL = "https://osskb.org/api/"

        /** The default list of ignored file extension. */
        private val DEFAULT_IGNORED_FILE_EXTENSIONS = listOf(
            ".1", ".2", ".3", ".4", ".5", ".6", ".7", ".8", ".9", ".ac", ".adoc", ".am", ".asciidoc", ".bmp", ".build",
            ".c9", ".c9revisions", ".cache", ".cfg", ".chm", ".class", ".cmake", ".cnf", ".conf", ".config",
            ".contributors", ".copying", ".cover", ".coverage", ".crt", ".csproj", ".css", ".csv", ".dat", ".data",
            ".doc", ".docx", ".dotcover", ".dtd", ".dts", ".dtsi", ".dump", ".editorconfig", ".egg", ".eot", ".eps",
            ".gdoc", ".gem", ".geojson", ".gif", ".glif", ".gml", ".gmo", ".gradle", ".guess", ".hex", ".htm", ".html",
            ".ico", ".iml", ".in", ".inc", ".info", ".ini", ".ipynb", ".iws", ".jpeg", ".jpg", ".json", ".jsonld",
            ".lcov", ".lock", ".log", ".lst", ".m4", ".manifest", ".map", ".markdown", ".md", ".md5", ".meta", ".mk",
            ".mxml", ".o", ".otf", ".out", ".pbtxt", ".pdb", ".pdf", ".pem", ".phtml", ".pickle", ".pid", ".plist",
            ".plt", ".png", ".po", ".pot", ".ppt", ".prefs", ".properties", ".pyc", ".qdoc", ".result", ".rgb", ".rst",
            ".scss", ".sha", ".sha1", ".sha2", ".sha256", ".sln", ".spec", ".sql", ".sub", ".svg", ".svn-base", ".tab",
            ".template", ".test", ".tex", ".tiff", ".toml", ".ttf", ".txt", ".utf-8", ".vim", ".wav", ".wfp", ".whl",
            ".woff", ".xht", ".xhtml", ".xls", ".xlsx", ".xml", ".xpm", ".xsd", ".xul", ".yaml", ".yml"
        )

        /** The default list of ignored file suffixes. */
        private val DEFAULT_IGNORED_FILE_SUFFIXES = DEFAULT_IGNORED_FILE_EXTENSIONS + listOf(
            "-doc", "authors", "changelog", "config", "copying", "ignore", "license", "licenses", "manifest", "news",
            "notice", "readme", "sqlite", "sqlite3", "swiftdoc", "texidoc", "todo", "version"
        )

        fun create(scannerConfig: ScannerConfiguration): ScanOssConfig {
            val scanOssOptions = scannerConfig.options?.get("ScanOss")

            val apiURL = scanOssOptions?.get(API_URL_PROPERTY) ?: DEFAULT_API_URL
            val apiKey = scanOssOptions?.get(API_KEY_PROPERTY).orEmpty()
            val ignoredFileSuffixes = scanOssOptions?.get(IGNORED_FILE_SUFFIXES_PROPERTY)
                ?.split(',')
                ?.map { it.trim() }
                ?: DEFAULT_IGNORED_FILE_SUFFIXES

            return ScanOssConfig(apiURL, apiKey, ignoredFileSuffixes)
        }
    }
}
