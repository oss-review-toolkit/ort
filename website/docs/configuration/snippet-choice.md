# The snippet choice feature

## Introduction

Snippets are short pieces of code.
They might come from different public sources such as GitHub, GitLab, or Stackoverflow.

So-called snippet scanners like ScanOSS and FossID scrape those public sources and build a knowledge base of snippets, classifying them by their author, version and license.
Then, the snippet scanner component of such products can use this knowledge base to find if some source file contains some of those snippets.
The matching of a snippet can be:

* *full*: the whole snippet matches the source file
* *partial*: only some parts of a snippet match the source file

Currently, ORT supports two products offering snippet scanner capabilities:
ScanOSS and FossID.
While each implementation is specific, the base functionality is the same:
ORT submits a source file to scan to the snippet scanner and receives the list of the snippets matching some parts of this source file.
ORT puts those snippets in the `SnippetFindings` property of the [ScanSummary](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/ScanSummary.kt).

## The problem

As mentioned in the previous section, ORT returns the list of snippet findings (i.e., matches) for a given source file.
These snippet findings are aggregated by source code location, including the line ranges.

ORT lacks a mechanism to control the snippets results: while some of these results are legitimate, others are false positives.
To decide which of these snippet findings are legitimate or false positives, the snippet choice feature allows the user to:

* Choose a snippet as the origin of the code snippet found in the project source code, discarding all other snippets for this source location.
* Mark all the snippets for a given source location as false positives.

Hence, the user can control which snippets are present in the ORT snippet results.

## Choosing a snippet

Let's say a source file `ci.yml` has been scanned with the following findings:

```yaml
snippets:
- source_location:
    path: ".github/workflows/ci.yml"
    start_line: 3
    end_line: 17
  snippets:
    - score: 0.93
      location:
        path: "dot_config/nvim/autoload/plugged/vim-devicons/dot_github/workflows/vint.yml"
        start_line: 3
        end_line: 18
      provenance:
        source_artifact:
          url: "https://github.com/RS2007/dotfiles/archive/0384a21038fd2e5befb429d0ca52384172607a6d.tar.gz"
          hash:
            value: ""
            algorithm: ""
      purl: "pkg:github/RS2007/dotfiles@0384a21038fd2e5befb429d0ca52384172607a6d"
      licenses: "MIT"
    - score: 0.93
      location:
        path: "private_dot_config/nvim/plugged/vim-devicons/dot_github/workflows/vint.yml"
        start_line: 3
        end_line: 18
      provenance:
        source_artifact:
          url: "https://github.com/stianfro/dotfiles/archive/b371008f262377599edac1c8ea23ef53da82f832.tar.gz"
          hash:
            value: ""
            algorithm: ""
      purl: "pkg:github/stianfro/dotfiles@b371008f262377599edac1c8ea23ef53da82f832"
      licenses: "Apache-2.0"
```

Now an operator decided that the snippet `pkg:github/RS2007/dotfiles@0384a21038fd2e5befb429d0ca52384172607a6d` is indeed a match and should be reflected in ORT results.
To do so, the user defines in the repository's `.ort.yml` the following **snippet choice**:

```yaml
snippet_choices:
- provenance:
    url: "https://github.com/vdurmont/semver4j.git"
  choices:
  - given:
      source_location:
        path: ".github/workflows/ci.yml"
        start_line: 3
        end_line: 17
    choice:
      purl: "pkg:github/RS2007/dotfiles@0384a21038fd2e5befb429d0ca52384172607a6d"
      reason: "ORIGINAL_FINDING"
      comment: "Explain why this snippet choice was made"
```

Three properties are required to identify the recipients of the snippet choice:

* `provenance.url` is the provenance of the repository of the source file
* `choices.given.source_location` identifies the source file receiving the snippet choice.
* `choices.choice.purl` is the Purl identifying the snippet

There is also another mandatory property:

* `choices.choice.reason` enum member of [SnippetChoiceReason](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/snippet/SnippetChoiceReason.kt).

Finally, one property is *informative* and aims at making the snippet choice configuration more maintainable:

* `choices.choice.comment` describes why the snippet choice was made.

The snippet choice is an iterative process:
one must first run ORT to get the snippets in the scan results.
Then, one or several snippets can be chosen in the `.ort.yml` file.
Then, ORT is run again to generate nw scan results, taking into account those chosen snippets.
This loop can be repeated as needed.

### What are the consequences of a snippet choice?

1. The license of the chosen snippet will be added to the license findings

   The consequences are *scanner specific*:

   * For FossID, these findings are usually coming from files that have been marked as identified.
     With the snippet choice, pending files with a chosen snippet should therefore be marked as identified with the license of the snippet as identification.
   * For ScanOSS, license findings are currently coming from *full matched* snippets.
     With the snippet choice, also files with a partial snippet match (and a chosen snippet) will have the license of the snippet as license finding.

2. For a chosen snippet of a given source code location, all the other snippets will be considered as **false positives** and be removed from the scan results.
   As it makes no sense to choose two snippets for the same source location, this feature allows keeping only those snippet findings that require attention in the scan results.
3. The snippets that have been chosen won't be visible in the FossID snippet report anymore.
4. The FossID files with a snippet choice are not *pending* anymore since they will be marked as identified.
   Consequently, they won't be counted in the special "pending files count" ORT issue created by the FossID scanner.

## Handling false positives

Continuing with the example from [above](snippet-choice.md#choosing-a-snippet), a problem remains:
How to deal with a source location that has *only* false positives snippets?
The solution is to use the `NO_RELEVANT_FINDING` reason in the `.ort.yml` file:

```yaml
snippet_choices:
- provenance:
    url: "https://github.com/vdurmont/semver4j.git"
  choices:
  - given:
      source_location:
        path: "CHANGELOG.md"
        start_line: 2
        end_line: 5
    choice:
      reason: "NO_RELEVANT_FINDING"
      comment: "Explain why this location has only false positives snippets"
```

Three properties are required to mark all the snippets for a given location as false positives:

* `provenance.url` is the provenance of the repository of the source file
* `choices.given.source_location` identifies the source file against which the snippets have been matched

There is also another mandatory property:

* `choices.choice.reason` always [SnippetChoiceReason.NO_RELEVANT_FINDING](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/snippet/SnippetChoiceReason.kt#L26).

And an optional one:

* `choices.choice.comment` describes why the snippet is a false positive.

### What are the consequences of snippets marked as *false positives* ?

1. The snippets that are *false positives* are removed from the scan results.
2. The snippets that are *false positives* won't be visible in the FossID snippet report.

## Snippet choice FAQ

Q:
*If the snippets with a snippet choice or a `NO_RELEVANT_FINDING` are not in the scan results anymore, what happens when there is a new snippet finding for this source location (e.g. after an update of the Knowledge base)?*

A:
Conceptually, the scan results that have been removed in a past run may have to be added again, with the addition of newly detected snippets.
How that can be achieved depends on the specific snippet scanner, as for example, FossID is stateful while ScanOSS is not.

Q:
*Identically, what happens if a snippet with a snippet choice or a `NO_RELEVANT_FINDING` is not present in the scanner Knowledge Base anymore, e.g. after an update?*

A:
This is problematic as it means the **.ort.yml** will be filled with snippet choices or *false positives* that are not relevant anymore, i.e. garbage data.
This is the responsibility of ORT to always query the full snippet findings and compare them with the entries in the `.ort.yml` file.
Then, an issue can be raised to notify the user that a snippet choice or a `NO_RELEVANT_FINDING` is not required anymore.
However, for FossID, querying the full snippet findings may be skipped if the state of FossID is used.

Q:
*What happens when the user deletes a previously chosen snippet in the .ort.yml file and retriggers a scan?*

A:
All the snippets for this source location need to be added again to the scan results.
Therefore, as with the previous question, the full snippet findings need to be queried.
And here too, in the case of FossID, its state can be used for that.
