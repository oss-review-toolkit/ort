/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

@file:Suppress("Filename", "MatchingDeclarationName")

package org.ossreviewtoolkit.model.utils

import com.fasterxml.jackson.databind.util.StdConverter

import java.util.SortedSet

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.DependencyGraphEdge
import org.ossreviewtoolkit.model.DependencyReference
import org.ossreviewtoolkit.model.FileList
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.SnippetFinding

class CopyrightFindingSortedSetConverter : StdConverter<Collection<CopyrightFinding>, SortedSet<CopyrightFinding>>() {
    override fun convert(value: Collection<CopyrightFinding>) = value.toSortedSet(CopyrightFinding.COMPARATOR)
}

class DependencyGraphEdgeSortedSetConverter :
    StdConverter<Collection<DependencyGraphEdge>, Set<DependencyGraphEdge>>() {
    override fun convert(value: Collection<DependencyGraphEdge>) = value.toSortedSet(compareBy({ it.from }, { it.to }))
}

class DependencyReferenceSortedSetConverter :
    StdConverter<Collection<DependencyReference>, SortedSet<DependencyReference>>() {
    override fun convert(value: Collection<DependencyReference>) =
        value.toSortedSet(compareBy({ it.pkg.toString() }, { it.fragment }))
}

/** Do not convert to SortedSet in order to not require a comparator consistent with equals */
class FileListSortedSetConverter : StdConverter<Collection<FileList>, Set<FileList>>() {
    override fun convert(value: Collection<FileList>) = value.sortedBy { it.provenance.getSortKey() }.toSet()
}

/** Do not convert to SortedSet in order to not require a comparator consistent with equals */
class FileListEntrySortedSetConverter : StdConverter<Collection<FileList.Entry>, Set<FileList.Entry>>() {
    override fun convert(value: Collection<FileList.Entry>) = value.sortedBy { it.path }.toSet()
}

class LicenseFindingSortedSetConverter : StdConverter<Collection<LicenseFinding>, SortedSet<LicenseFinding>>() {
    override fun convert(value: Collection<LicenseFinding>) = value.toSortedSet(LicenseFinding.COMPARATOR)
}

class PackageReferenceSortedSetConverter : StdConverter<Collection<PackageReference>, SortedSet<PackageReference>>() {
    override fun convert(value: Collection<PackageReference>) = value.toSortedSet(compareBy { it.id })
}

class PackageSortedSetConverter : StdConverter<Collection<Package>, SortedSet<Package>>() {
    override fun convert(value: Collection<Package>) = value.toSortedSet(compareBy { it.id })
}

class ProjectSortedSetConverter : StdConverter<Collection<Project>, SortedSet<Project>>() {
    override fun convert(value: Collection<Project>) = value.toSortedSet(compareBy { it.id })
}

class ProvenanceResolutionResultSortedSetConverter :
    StdConverter<Collection<ProvenanceResolutionResult>, SortedSet<ProvenanceResolutionResult>>() {
    override fun convert(value: Collection<ProvenanceResolutionResult>) = value.toSortedSet(compareBy { it.id })
}

class ScannersMapConverter : StdConverter<Map<Identifier, Set<String>>, Map<Identifier, Set<String>>>() {
    override fun convert(value: Map<Identifier, Set<String>>) = value.mapValues { it.value.toSortedSet() }.toSortedMap()
}

/** Do not convert to SortedSet in order to not require a comparator consistent with equals */
class ScanResultSortedSetConverter : StdConverter<Collection<ScanResult>, Set<ScanResult>>() {
    override fun convert(value: Collection<ScanResult>) = value.sortedBy { it.provenance.getSortKey() }.toSet()
}

class ScopeSortedSetConverter : StdConverter<Collection<Scope>, SortedSet<Scope>>() {
    override fun convert(value: Collection<Scope>) = value.toSortedSet(compareBy { it.name })
}

class SnippetFindingSortedSetConverter : StdConverter<Collection<SnippetFinding>, SortedSet<SnippetFinding>>() {
    override fun convert(value: Collection<SnippetFinding>) = value.toSortedSet(compareBy { it.sourceLocation })
}

private fun Provenance.getSortKey(): String =
    buildList {
        add(javaClass.canonicalName)

        when (this@getSortKey) {
            is RepositoryProvenance -> {
                add(vcsInfo.type.toString())
                add(vcsInfo.url)
            }

            is ArtifactProvenance -> {
                add(sourceArtifact.url)
            }

            else -> {
                // Do not add anything, because unknown provenance does not have any properties.
            }
        }
    }.joinToString()

// TODO: Add more converters to get rid of Comparable implementations that just serve sorted output.
