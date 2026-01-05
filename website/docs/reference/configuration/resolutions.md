# Resolutions

Resolutions allow you to *resolve* issues, policy rule violations or vulnerabilities by providing a reason why they are acceptable and can be ignored.

Resolutions are only taken into account by the [ORT Reporter](../cli/reporter.md), while the [ORT Analyzer](../cli/analyzer.md) and [ORT Scanner](../cli/scanner.md) ignore them.

## When to use

Use a resolution to:

* Mark tool issues as resolved, typically in cases of:
  * license scanner detection timeouts
  * unavailable package sources
* Mark policy rule violations as resolved when the policy requires:
  * confirmation that a dependency was not modified or is dynamically linked
  * verification that a license was acquired for proprietary software
* Mark detected vulnerabilities as resolved for false positives, such as:
  * unreachable or non-executable code linked to a known vulnerability
  * invalid matched vulnerabilities
  * orphaned packages or those declared end-of-life that will not be fixed

To apply resolutions to each scan made create a `resolutions.yml` file, put it to `$ORT_CONFIG_DIR` directory or pass it via the `--resolutions-file` option to the [ORT Reporter](../cli/reporter.md). If a resolution is project-specific, then add it in the [.ort.yml](ort-yml.md) file for the project.

⚠️ **Resolutions should not be used to resolve license policy rule violations** as they do not the change generated open source notices (e.g. NOTICE files).
To resolve a license policy rule violation, either add a local `license_findings` curation to the [.ort.yml file](./ort-yml.md) if the finding is in your code repository or create a [package configuration](package-curations.md) with a `license_finding_curations` if the violation occurs in a dependency.

## File format

A resolution addresses specific issues, violations, or vulnerabilities through the regular expression specified in the `message`. Each resolution must include an explanation to clarify its acceptability, comprising:

* `reason` - an identifier selected from a predefined list of options either a
  * [IssueResolutionReason][issueResolutionReason] for tool issue resolutions,
  * [RuleViolationResolutionReason][RuleViolationResolutionReason] for policy violation resolutions or,
  * [VulnerabilityResolutionReason][VulnerabilityResolutionReason] for security vulnerability resolutions.
* `comment` - free text, providing an explanation and optionally a link to further information.

The code below shows the structure of `ort.yml` file.

⚠️ The resolutions file format used in `.ort.yml` is slightly different from that of `resolutions.yml`, as the latter does not require the `resolutions` key.

```yaml
---
resolutions: # Only include for .ort.yml, omit for resolutions.yml
  issues:
  - message: "A regular expression matching the error message."
    reason: >
      One of IssueResolutionReason e.g.
      BUILD_TOOL_ISSUE,
      CANT_FIX_ISSUE or
      SCANNER_ISSUE.
    comment: |
      An explanation why the resolution is acceptable.
      It’s recommended to include links to the relevant code or ticket to support your explanation.
  rule_violations:
  - message: "A regular expression matching the policy violation message."
    reason: >
      One of RuleViolationResolutionReason e.g.
      CANT_FIX_EXCEPTION,
      DYNAMIC_LINKAGE_EXCEPTION,
      EXAMPLE_OF_EXCEPTION,
      LICENSE_ACQUIRED_EXCEPTION,
      NOT_MODIFIED_EXCEPTION or
      PATENT_GRANT_EXCEPTION.
    comment: |
      An explanation why the resolution is acceptable.
      It’s recommended to include links to the relevant code or ticket to support your explanation.
  vulnerabilities:
  - id: "A regular expression matching the vulnerability id."
    reason: >
      One of VulnerabilityResolutionReason e.g.
      CANT_FIX_VULNERABILITY,
      INEFFECTIVE_VULNERABILITY,
      INVALID_MATCH_VULNERABILITY,
      MITIGATED_VULNERABILITY,
      NOT_A_VULNERABILITY,
      WILL_NOT_FIX_VULNERABILITY or
      WORKAROUND_FOR_VULNERABILITY.
    comment: |
      An explanation why the resolution is acceptable.
      It’s recommended to include links to the relevant code or ticket to support your explanation.
```

## Command line

To use the `resolutions.yml` file put it to `$ORT_CONFIG_DIR` directory or pass it to the `--resolutions-file` option of the [ORT Reporter](../cli/reporter.md).

```shell
cli/build/install/ort/bin/ort report \
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats PlainTextTemplate,WebApp \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --resolutions-file $ORT_CONFIG_DIR/resolutions.yml
```

Alternatively, you can also use the ORT docker image.

```shell
docker run ghcr.io/oss-review-toolkit/ort report \
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats PlainTextTemplate,WebApp \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --resolutions-file $ORT_CONFIG_DIR/resolutions.yml
```

## Related resources

* Code
  * [model/src/main/kotlin/config/Resolutions.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/Resolutions.kt)
  * [model/src/main/kotlin/config/IssueResolutionReason.kt][IssueResolutionReason]
  * [model/src/main/kotlin/config/RuleViolationResolutionReason.kt][RuleViolationResolutionReason]
  * [model/src/main/kotlin/config/VulnerabilityResolutionReason.kt][VulnerabilityResolutionReason]
* Examples
  * [examples/resolutions.yml](https://github.com/oss-review-toolkit/ort/blob/main/examples/resolutions.yml)
  * [examples/resolutions.ort.yml](https://github.com/oss-review-toolkit/ort/blob/main/examples/resolutions.ort.yml)
  * [resolutions.yml within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/blob/main/resolutions.yml)
* How-to guides
  * [How to address tool issues](../../how-to-guides/how-to-address-tool-issues.md)
  * [How to address a license policy violation](../../how-to-guides/how-to-address-a-license-policy-violation.md)
  * [How to remediate a vulnerability in a dependency](../../how-to-guides/how-to-check-and-remediate-vulnerabilities-in-dependencies.md)
* JSON schema
  * [integrations/schemas/resolutions-schema.json](https://github.com/oss-review-toolkit/ort/blob/main/integrations/schemas/resolutions-schema.json)
* Reference
  * [Advisor CLI --resolutions-file option](../cli/advisor.md#configuration-options)
  * [Helper CLI --generate-timeout-error-resolutions command](../cli/orth.md#commands)
  * [Reporter CLI --resolutions-file option](../cli/reporter.md#configuration-options)

  [IssueResolutionReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/IssueResolutionReason.kt
  [RuleViolationResolutionReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/RuleViolationResolutionReason.kt
  [VulnerabilityResolutionReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/VulnerabilityResolutionReason.kt
