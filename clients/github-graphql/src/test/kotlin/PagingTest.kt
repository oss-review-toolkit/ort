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

package org.ossreviewtoolkit.clients.github

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class PagingTest : WordSpec({
    "Paging" should {
        "fail for a negative page size" {
            shouldThrow<IllegalArgumentException> {
                Paging(pageSize = 0)
            }
        }

        "fail for a page size that is too large" {
            shouldThrow<IllegalArgumentException> {
                Paging(pageSize = Paging.MAX_PAGE_SIZE + 1)
            }
        }
    }

    "fetchAll" should {
        "iterate over all pages" {
            val queryPageSize = 2
            val maxPage = 3

            val queryFunc: suspend (Paging) -> QueryResult<String> = { paging ->
                val pageNo = paging.cursor?.toInt() ?: 0
                val items = (1..(paging.pageSize)).map { "R${pageNo * paging.pageSize + it}" }
                val nextCursor = if (pageNo < maxPage) (pageNo + 1).toString() else null
                Result.success(PagedResult(items, paging.pageSize, nextCursor))
            }

            val aggregateFunc: (MutableList<String>, PagedResult<String>) -> MutableList<String> = { list, result ->
                result.pageSize shouldBe queryPageSize
                list += result.items
                list
            }

            val allResult = Paging.fetchAll(mutableListOf(), queryFunc, aggregateFunc, queryPageSize)

            allResult.shouldBeSuccess { list ->
                list should containExactly("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8")
            }
        }

        "handle failures during iteration" {
            val errorPageNo = 5
            val exception = IllegalStateException("Error page")
            val queryFunc: suspend (Paging) -> QueryResult<String> = { paging ->
                val nextCursor = paging.cursor.orEmpty() + "*"
                nextCursor.takeIf { it.length < errorPageNo }?.let { cursor ->
                    Result.success(PagedResult(emptyList(), paging.pageSize, cursor))
                } ?: Result.failure(exception)
            }

            val aggregateFunc: (String, PagedResult<String>) -> String = { str, result ->
                result.cursor.orEmpty().length shouldBeLessThan errorPageNo
                "$str~"
            }

            val allResult = Paging.fetchAll("", queryFunc, aggregateFunc, 42)

            allResult.exceptionOrNull() shouldBe exception
        }
    }
})
