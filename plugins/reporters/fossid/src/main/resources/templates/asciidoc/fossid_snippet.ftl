[#--
    Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
    License-Filename: LICENSE
--]

:publisher: OSS Review Toolkit
[#assign now = .now]
:revdate: ${now?date?iso_local}

:title-page:
:sectnums:
:toc:

= FossID Snippets
List of all the provenances with their files and snippets.
[#list ortResult.scanner.scanResults as scanResult]

[#if scanResult.scanner.name != "FossId"] [#continue] [/#if]

[#if scanResult.provenance.vcsInfo??]
    [#assign url = scanResult.provenance.vcsInfo.url]
[#else]
    [#assign url = scanResult.provenance.sourceArtifact.url]
[/#if]
== Provenance '${url}'

[#assign summary = scanResult.summary]

Scan start time : ${summary.startTime} +
End time : ${summary.startTime} +
[#if scanResult.provenance.vcsInfo??]
    [#assign gitRepoUrl = url]
    [#assign gitRevision = scanResult.provenance.vcsInfo.revision]
    Git repo URL: ${gitRepoUrl} +
    Git revision: ${gitRevision}

    [#if gitRepoUrl?contains("github.com")]
        [#assign githubBaseURL = '${gitRepoUrl?remove_ending(".git")}/blob/${gitRevision}']
    [/#if]
[/#if]

[#assign snippets = helper.groupSnippetsByFile(summary.snippetFindings)]

[#list snippets as filePath, snippetFindings]
[#if gitRepoUrl?? && gitRepoUrl?contains("github.com")]
  [#assign localFileURL = '_${githubBaseURL}/${filePath}[source]_']
[#else]
  [#assign localFileURL = "_source_"]
[/#if]

[#assign licenses = helper.collectLicenses(snippetFindings)]

*${filePath}* +
License(s):
[#list licenses as license]
  ${license}[#sep],
[/#list]

[width=100%]
[cols="1,3,1,3,3,1,1"]
|===
| Match | pURL | License | File | URL | Score | Release Date

[#list snippetFindings as snippetFinding ]
[#assign snippet = snippetFinding.snippet]
[#assign matchType = snippet.additionalData["matchType"]]
| ${matchType} | ${snippet.purl!""}
| ${snippet.licenses!""} | ${snippet.location.path!""} | ${snippet.provenance.sourceArtifact.url!""}[URL]
| ${snippet.score!""} | ${snippet.additionalData["releaseDate"]}
[#if matchType == "PARTIAL"]
2+^| *Matched lines* 5+| ${localFileURL}: ${snippet.additionalData["matchedLinesSource"]} /
    _remote_: ${snippet.additionalData["matchedLinesSnippet"]}
[/#if]
[/#list]
|===
[/#list]

[/#list]
