/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.licenses

import io.kotest.matchers.Matcher
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.neverNullMatcher

import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

object TestUtils {
    fun containLicensesExactly(vararg licenses: String): Matcher<Iterable<ResolvedLicense>?> =
        neverNullMatcher { value ->
            val expected = licenses.map { SpdxExpression.parse(it) as SpdxSingleLicenseExpression }.toSet()
            val actual = value.map { it.license }.toSet()

            containExactly(expected).test(actual)
        }
}
