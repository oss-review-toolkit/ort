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

/**
 * A data class to define the paging to be applied to a GraphQL query.
 *
 * Using this class, the exact page (the start index and the number of entries to retrieve) to be returned by a query
 * can be determined.
 */
data class Paging(
    /**
     * Defines the size of the page. This is the number of entries that are retrieved.
     */
    val pageSize: Int = MAX_PAGE_SIZE,

    /**
     * A cursor that can be used to iterate over all the pages of a query result. For the first page, the cursor is
     * *null*. Then each [PagedResult] contains a cursor to select the next page as long as more data is available.
     */
    val cursor: String? = null
) {
    companion object {
        /** The maximum number of entries on a page allowed by the GitHub API. */
        const val MAX_PAGE_SIZE = 100

        /**
         * Constant for a default [Paging] object to retrieve the first page of a query with the maximum number of
         * entries on it.
         */
        val INITIAL = Paging()

        /**
         * Fetch all pages of a query result and return an aggregated result. This helper function simplifies the
         * handling of paging for clients that are only interested in an aggregated result, computed from all result
         * pages. The function generates [Paging] objects to retrieve all pages with a size of [pageSize]. The
         * provided [query] function is invoked for each page. The results produced by this function are then passed
         * to [aggregate], using [init] as the initial value of the aggregation.
         */
        suspend fun <T, A> fetchAll(
            init: A,
            query: suspend (Paging) -> QueryResult<T>,
            aggregate: (A, PagedResult<T>) -> A,
            pageSize: Int = MAX_PAGE_SIZE
        ): Result<A> {
            var paging = Paging(pageSize = pageSize)
            var aggregateResult = init
            var done = false

            do {
                val result = query(paging)
                result.fold(
                    onSuccess = { pagedResult ->
                        aggregateResult = aggregate(aggregateResult, pagedResult)
                        done = pagedResult.cursor == null
                        if (!done) {
                            paging = paging.copy(cursor = pagedResult.cursor)
                        }
                    },
                    onFailure = { return Result.failure(it) }
                )
            } while (!done)

            return Result.success(aggregateResult)
        }
    }

    init {
        require(pageSize in 1..MAX_PAGE_SIZE) { "pageSize must be > 0 and <= $MAX_PAGE_SIZE" }
    }
}

/**
 * A data class representing a result of a GraphQL query together with paging information.
 *
 * The GitHub API uses paging for most entities. The maximum number of entries per page is 100; so for bigger
 * repositories, typically multiple pages need to be requested. Instances of this class contain a cursor that can be
 * passed to query functions in order to retrieve the next page.
 */
data class PagedResult<T>(
    /** A list with the actual result items returned by a query. */
    val items: List<T>,

    /** The page size configured by the request. */
    val pageSize: Int,

    /**
     * A cursor allowing to request the next page of a query result. The field is *null* if there are no further
     * pages.
     */
    val cursor: String?
)

/**
 * An alias for a result of a GraphQL query. The result contains additional paging information. It can be a failure if
 * the query was not successful.
 */
typealias QueryResult<T> = Result<PagedResult<T>>
