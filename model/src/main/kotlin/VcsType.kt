/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * A class for Version Control System types. Each type has one or more [aliases] associated to it, where the first
 * alias is the definite name.
 */
data class VcsType(val aliases: List<String>) {
    /**
     * A constructor that searches [all known VCS aliases][ALL_ALIASES] for the given [type] and creates an instance
     * with matching aliases, or an instance with only [type] as the alias if no aliases match.
     */
    @JsonCreator
    constructor(type: String) : this(
        ALL_ALIASES.find { aliases ->
            aliases.any { alias ->
                alias.equals(type, ignoreCase = true)
            }
        } ?: listOf(type)
    )

    companion object {
        /**
         * [Git](https://git-scm.com/) - the stupid content tracker.
         */
        val GIT = VcsType(listOf("Git"))

        /**
         * [Repo](https://source.android.com/setup/develop/repo) complements Git by simplifying work across multiple
         * repositories.
         */
        val GIT_REPO = VcsType(listOf("GitRepo", "git-repo", "repo"))

        /**
         * [Mercurial](https://www.mercurial-scm.org/) is a free, distributed source control management tool.
         */
        val MERCURIAL = VcsType(listOf("Mercurial", "hg"))

        /**
         * [Subversion](https://subversion.apache.org/) is an open source Version Control System.
         */
        val SUBVERSION = VcsType(listOf("Subversion", "svn"))

        /**
         * The [Concurrent Versions System](https://en.wikipedia.org/wiki/Concurrent_Versions_System), not actively
         * developed anymore.
         */
        val CVS = VcsType(listOf("CVS"))

        private val ALL_ALIASES = listOf(
            GIT.aliases,
            GIT_REPO.aliases,
            MERCURIAL.aliases,
            SUBVERSION.aliases,
            CVS.aliases
        )

        /**
         * An unknown VCS type.
         */
        val UNKNOWN = VcsType(listOf(""))
    }

    @JsonValue
    override fun toString(): String = aliases.first()
}
