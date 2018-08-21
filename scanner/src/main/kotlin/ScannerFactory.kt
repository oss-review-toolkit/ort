/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.scanner

import com.here.ort.model.config.ScannerConfiguration

/**
 * A common interface for use with [ServiceLoader] that all [AbstractScannerFactory] classes need to implement.
 */
interface ScannerFactory {
    /**
     * Create a [Scanner] using the specified [config].
     */
    fun create(config: ScannerConfiguration): Scanner
}

/**
 * A generic factory class for a [Scanner].
 */
abstract class AbstractScannerFactory<out T : Scanner> : ScannerFactory {
    abstract override fun create(config: ScannerConfiguration): T

    /**
     * Return the Java class name as a simple way to refer to the [AbstractScannerFactory]. As factories are supposed to
     * be implemented as inner classes we need to manually strip unwanted parts of the fully qualified name.
     */
    override fun toString() = javaClass.name.substringBefore('$').substringAfterLast('.')
}
