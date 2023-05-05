/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.test

import io.kotest.matchers.Matcher
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.neverNullMatcher

/**
 * A helper function to create a custom matcher that compares an [expected] collection to a collection obtained by
 * [transform] using the provided [matcher].
 */
fun <T, U> transformingCollectionMatcher(
    expected: Collection<U>,
    matcher: (Collection<U>) -> Matcher<Collection<U>>,
    transform: (T) -> Collection<U>
): Matcher<T?> = neverNullMatcher { value -> matcher(expected).test(transform(value)) }

/**
 * A helper function to create custom matchers that assert that the collection obtained by [transform] is empty.
 */
fun <T, U> transformingCollectionEmptyMatcher(
    transform: (T) -> Collection<U>
): Matcher<T?> = neverNullMatcher { value -> beEmpty<U>().test(transform(value)) }
