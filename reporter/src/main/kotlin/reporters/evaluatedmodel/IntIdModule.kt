/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.evaluatedmodel

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.PropertyName
import com.fasterxml.jackson.databind.introspect.Annotated
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import com.fasterxml.jackson.databind.introspect.ObjectIdInfo
import com.fasterxml.jackson.databind.module.SimpleModule

/**
 * A Jackson [Module] that configures the [ObjectIdInfo] for the provided [types]. The configuration applied to these
 * classes is similar to adding the following annotation to a class `C`:
 *
 * ```
 * @JsonIdentityInfo(property = "_id", generator = ZeroBasedIntSequenceGenerator::class, scope = C::class)
 * ```
 *
 * This module is useful for adding object IDs to classes where the annotation cannot be added. The advantage of this
 * approach over using Jackson mixins is that a separate mixin would be required for each class to set the scope
 * correctly.
 */
class IntIdModule(private val types: List<Class<out Any>>) : SimpleModule() {
    override fun setupModule(context: SetupContext) {
        super.setupModule(context)

        context.appendAnnotationIntrospector(object : JacksonAnnotationIntrospector() {
            override fun findObjectIdInfo(ann: Annotated): ObjectIdInfo? {
                if (ann.rawType in types) {
                    return ObjectIdInfo(
                        PropertyName("_id"),
                        ann.rawType,
                        ZeroBasedIntSequenceGenerator::class.java,
                        null
                    )
                }
                return super.findObjectIdInfo(ann)
            }
        })
    }
}
