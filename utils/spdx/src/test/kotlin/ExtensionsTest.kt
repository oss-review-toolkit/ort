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

package org.ossreviewtoolkit.utils.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.beEmpty

class ExtensionsTest : WordSpec({
    "toSpdxId()" should {
        "convert license strings" {
            "GPL-2.0+".toSpdxId() shouldBe "GPL-2.0"
            "GPL-2.0+".toSpdxId(allowPlusSuffix = true) shouldBe "GPL-2.0+"
        }

        "convert ORT Identifier coordinate strings" {
            "Maven:org.eclipse.jetty:jetty-client:9.4.42.v20210604".toSpdxId() shouldBe
                    "Maven-org.eclipse.jetty-jetty-client-9.4.42.v20210604"
            "Pub:crypto:crypto:2.1.1+1".toSpdxId(allowPlusSuffix = true) shouldBe "Pub-crypto-crypto-2.1.1.1"
            "NPM::lodash._reinterpolate:3.0.0".toSpdxId() shouldBe "NPM-lodash.reinterpolate-3.0.0"
        }

        "convert arbitrary strings" {
            "+!§$%&/()=?\\:+".toSpdxId() should beEmpty()
            "+!§$%foo&/(bar)=?\\:+".toSpdxId(allowPlusSuffix = true) shouldBe "foo.bar+"
        }
    }
})
