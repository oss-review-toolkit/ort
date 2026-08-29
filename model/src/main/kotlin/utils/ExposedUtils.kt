/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi

/**
 * Execute a [SHOW][1] query and return the [transformed][transform] result. This function is required because
 * [JdbcTransaction.exec] only expects a result for SELECT queries and throws an exception if other queries return a
 * result.
 *
 * [1]: https://www.postgresql.org/docs/current/sql-show.html
 */
fun <T : Any> JdbcTransaction.execShow(statement: String, transform: (ResultSet) -> T): T? {
    if (statement.isEmpty()) return null

    require(statement.startsWith("SHOW")) { "Query must start with 'SHOW'." }

    return exec(object : BlockingExecutable<T, Statement<T>> {
        override val statement = object : Statement<T>(StatementType.OTHER, emptyList()) {
            override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = emptyList()

            override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = statement
        }

        override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): T =
            executeQuery().use { transform(it.result) }
    })
}

class RawExpression(val value: String) : Expression<String>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(value) }
}

fun arrayParam(array: IntArray) = RawExpression("ARRAY[${array.joinToString(separator = ",")}]")

fun rawParam(value: String) = RawExpression(value)

class TildeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "~")

infix fun <T : String?> Expression<T>.tilde(pattern: String) = TildeOp(this, stringParam(pattern))
