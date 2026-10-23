/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.spdxexpression

/**
 * An SPDX simple expression as defined by version 2.2 of the [SPDX specification, annex D.3][1]. A simple expression
 * can be either an [SpdxLicenseIdExpression] or an [SpdxLicenseReferenceExpression].
 *
 * [1]: https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/#d3-simple-license-expressions
 */
sealed class SpdxSimpleExpression : SpdxSingleLicenseExpression() {
    /**
     * Concatenate [this][SpdxSimpleExpression] and [other] using [SpdxExpression.WITH].
     */
    infix fun with(other: String) = SpdxLicenseWithExceptionExpression(this, other)
}
