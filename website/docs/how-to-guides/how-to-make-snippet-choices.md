# How to make snippet choices

Snippet scanners like FossID and ScanOSS may return multiple matches for the same piece of code in your project. Use [snippet choices][ort-yml-snippet-choices] to select the correct origin or mark findings as false positives.

## Selecting the correct snippet origin

When a scanner returns multiple matches for a code snippet, choose the correct origin in your `.ort.yml` file:

```yaml
snippet_choices:
- provenance:
    url: "https://github.com/your-org/your-project.git"
  choices:
  - given:
      source_location:
        path: "src/main/java/Version.java"
        start_line: 3
        end_line: 17
    choice:
      purl: "pkg:github/vdurmont/semver4j@1.0.0"
      reason: "ORIGINAL_FINDING"
      comment: "This is the original source of the version parsing code."
```

## Marking snippets as false positives

To mark a snippet finding as a false positive, use the `NO_RELEVANT_FINDING` reason:

```yaml
snippet_choices:
- provenance:
    url: "https://github.com/your-org/your-project.git"
  choices:
  - given:
      source_location:
        path: "src/utils/helpers.js"
        start_line: 10
        end_line: 25
    choice:
      reason: "NO_RELEVANT_FINDING"
      comment: "Generic utility code, not copied from any external source."
```

## Related resources

* Reference
  * [Repository configuration (.ort.yml)][ort-yml]

[ort-yml]: ../reference/configuration/ort-yml.md
[ort-yml-snippet-choices]: ../reference/configuration/ort-yml.md#snippet-choices
