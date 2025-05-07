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

[#assign snippetsLimitIssue = helper.getSnippetsLimitIssue()]

[#if snippetsLimitIssue?has_content]
[WARNING]
====
${snippetsLimitIssue}
====
[/#if]

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

[#list helper.groupSnippetsByFile(summary.snippetFindings) as filePath, snippetFindings ]

[#if gitRepoUrl?? && gitRepoUrl?contains("github.com")]
    [#assign localFileURL = '${githubBaseURL}/${filePath}[${filePath}]']
[#else]
    [#assign localFileURL = "${filePath}"]
[/#if]
[#assign licenses = helper.collectLicenses(snippetFindings)]

*${localFileURL}* +
License(s):
[#list licenses as license]
  ${license}[#sep],
[/#list]

[#list helper.groupSnippetsBySourceLines(snippetFindings) as sourceLocation, snippetFinding]
[#assign snippetCount = snippetFinding.snippets?size]

[width=100%]
[cols="1,3,1,3,3,1,1"]
|===
| Source Location | pURL | License | File | URL | Score | Release Date

.${snippetCount*2}+|
[#if sourceLocation.hasLineRange]
Partial match +
${sourceLocation.startLine?c}-${sourceLocation.endLine?c}
[#else]
Full match
[/#if]

[#list snippetFinding.snippets as snippet ]
[#assign matchType = snippet.additionalData["matchType"]]
[#assign snippetFilePath = snippet.location.path!""]
[#if matchType == "PARTIAL" && snippetFilePath?has_content && snippet.additionalData['matchedLinesSnipped']?has_content]
    [#assign snippetFilePath = "${snippetFilePath}#${snippet.additionalData['matchedLinesSnippet']}"]
[/#if]

| ${snippet.purl!""}
| ${snippet.licenses!""} | ${snippetFilePath} | ${snippet.provenance.sourceArtifact.url!""}[URL]
| ${snippet.score!""} | ${snippet.additionalData["releaseDate"]}
6+a|
.Create a snippet choice for this snippet or mark it as false positive
[%collapsible]
====
Add the following lines to the *.ort.yml* file.

To **choose** this snippet:
[source,yaml]
--
snippet_choices:
- provenance:
    url: "${scanResult.provenance.vcsInfo.url}"
  choices:
  - given:
      source_location:
        path: "${filePath}"
        start_line: ${snippetFinding.sourceLocation.startLine?c}
        end_line: ${snippetFinding.sourceLocation.endLine?c}
    choice:
      purl: "${snippet.purl!""}"
      reason: "ORIGINAL_FINDING"
      comment: "Explain why this snippet choice was made"
--
Or to mark this location has having ONLY **false positives snippets**:
[source,yaml]
--
snippet_choices:
- provenance:
    url: "${scanResult.provenance.vcsInfo.url}"
  choices:
  - given:
      source_location:
        path: "${filePath}"
        start_line: ${snippetFinding.sourceLocation.startLine?c}
        end_line: ${snippetFinding.sourceLocation.endLine?c}
    choice:
      reason: "NO_RELEVANT_FINDING"
      comment: "Explain why this location has only false positives snippets"
--
====
[/#list]
|===
[/#list]
[/#list]
[/#list]
