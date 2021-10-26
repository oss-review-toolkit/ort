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

import com.fasterxml.jackson.annotation.ObjectIdGenerator
import com.fasterxml.jackson.annotation.ObjectIdGenerators

/**
 * This [ObjectIdGenerator] works similar to [ObjectIdGenerators.IntSequenceGenerator] with the difference that the
 * initialValue is 0 instead of 1.
 */
class ZeroBasedIntSequenceGenerator @JvmOverloads constructor(
    private val scope: Class<*> = Any::class.java,
    @field:Transient private var nextValue: Int = -1
) : ObjectIdGenerator<Int>() {
    companion object {
        private const val INITIAL_VALUE = 0
    }

    override fun canUseFor(gen: ObjectIdGenerator<*>): Boolean = gen.javaClass == javaClass && gen.scope == scope

    override fun forScope(scope: Class<*>): ObjectIdGenerator<Int> =
        if (this.scope == scope) this else ZeroBasedIntSequenceGenerator(scope, nextValue)

    override fun generateId(forPojo: Any?): Int? {
        if (forPojo == null) return null

        val id = nextValue
        ++nextValue
        return id
    }

    override fun getScope(): Class<*> = scope

    override fun key(key: Any?): IdKey? = key?.let { IdKey(javaClass, scope, it) }

    override fun newForSerialization(context: Any?): ObjectIdGenerator<Int> =
        ZeroBasedIntSequenceGenerator(scope, INITIAL_VALUE)
}
