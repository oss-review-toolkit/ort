/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object HashAlgorithmSerializer : KSerializer<HashAlgorithm> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.ossreviewtoolkit.model.HashAlgorithm", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: HashAlgorithm) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): HashAlgorithm {
        val string = decoder.decodeString()
        return HashAlgorithm.fromString(string)
    }
}
