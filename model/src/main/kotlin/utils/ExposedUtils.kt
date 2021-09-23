/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import java.sql.ResultSet

import org.jetbrains.exposed.sql.ComparisonOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.stringParam

/**
 * Execute a [SHOW][1] query and return the [transformed][transform] result. This function is required because
 * [Transaction.exec] only expects a result for SELECT queries and throws an exception if other queries return a result.
 *
 * [1]: https://www.postgresql.org/docs/current/sql-show.html
 */
fun <T : Any> Transaction.execShow(statement: String, transform: (ResultSet) -> T): T? {
    if (statement.isEmpty()) return null

    require(statement.startsWith("SHOW")) { "Query must start with 'SHOW'." }

    return exec(object : Statement<T>(StatementType.SELECT, emptyList()) {
        override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = emptyList()

        override fun prepareSQL(transaction: Transaction): String = statement

        override fun PreparedStatementApi.executeInternal(transaction: Transaction): T =
            executeQuery().use { transform(it) }
    })
}

class RawExpression(val value: String) : Expression<String>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(value) }
}

fun arrayParam(array: IntArray) = RawExpression("ARRAY[${array.joinToString(separator = ",")}]")

fun rawParam(value: String) = RawExpression(value)

class TildeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "~")

infix fun <T : String?> Expression<T>.tilde(pattern: String) = TildeOp(this, stringParam(pattern))
