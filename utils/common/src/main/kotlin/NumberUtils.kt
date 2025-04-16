/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.common

/**
 * Return the bytes equal to this [Int] number of kibibytes (KiB).
 */
inline val Int.kibibytes get(): Long = this * 1024L

/**
 * Return the bytes equal to this [Int] number of mebibytes (MiB).
 */
inline val Int.mebibytes get(): Long = kibibytes * 1024L

/**
 * Return the bytes equal to this [Int] number of gibibytes (GiB).
 */
inline val Int.gibibytes get(): Long = mebibytes * 1024L

/**
 * Format this [Double] as a string with the provided number of [decimalPlaces].
 */
fun Double.format(decimalPlaces: Int = 2) = "%.${decimalPlaces}f".format(this)

/**
 * Converts this [Number] from bytes to mebibytes (MiB).
 */
fun Number.bytesToMib(): Double = toDouble() / 1.mebibytes
