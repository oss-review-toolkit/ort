/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.tycho

import java.io.File
import java.io.InputStream

import javax.xml.parsers.SAXParserFactory

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Type alias for a function that can process XML start element elements. The function is passed the current state
 * and the attributes of the element. It returns the updated state. Note that the XML documents that need to be
 * parsed in this context store all their information in attributes; there is no element content. Therefore, it is
 * sufficient to react only on start element events.
 */
internal typealias ElementStartFun<S> = (S, Attributes) -> S

/**
 * A handler class for parsing XML documents that allows to register special functions for specific elements.
 * An instance maintains a state of type [S] that is updated while processing the encountered elements.
 * The class is tailored to the specific requirements of the XML documents used by Tycho. It expects that all relevant
 * information is available in the attributes of XML elements.
 */
internal class ElementHandler<S : Any>(initialState: S) : DefaultHandler() {
    /** Stores the element start functions registered at this handler. Key is the element name. */
    private val elementStartFunctions = mutableMapOf<String, ElementStartFun<S>>()

    /** Stores the current state during XML processing. */
    private var currentState: S = initialState

    /**
     * Return the resulting state after processing the XML document.
     */
    val result: S
        get() = currentState

    /**
     * Register the given [startFunction] for the element with the given [name].
     */
    fun handleElement(name: String, startFunction: ElementStartFun<S>): ElementHandler<S> {
        elementStartFunctions[name] = startFunction
        return this
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        elementStartFunctions[qName]?.also { f ->
            currentState = f(currentState, attributes)
        }
    }
}

/**
 * Parse the given [stream] with an XML document using the given [handler] and return the result produced by the
 * [handler]. Callers are responsible for closing the [stream].
 */
internal fun <S : Any> parseXml(stream: InputStream, handler: ElementHandler<S>): S {
    val factory = SAXParserFactory.newInstance()
    val parser = factory.newSAXParser()

    parser.parse(stream, handler)

    return handler.result
}

/**
 * Parse the given XML [file] using the given [handler] and return the result produced by the [handler].
 */
internal fun <S : Any> parseXml(file: File, handler: ElementHandler<S>): S =
    file.inputStream().use { stream -> parseXml(stream, handler) }
