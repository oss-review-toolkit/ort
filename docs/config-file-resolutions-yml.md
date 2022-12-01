# The `resolutions.yml` file

Resolutions allow you to *resolve* issues, policy rule violations or vulnerabilities by providing a reason why they are
acceptable and can be ignored.

You can use the [resolutions.yml example](../examples/resolutions.yml) as the base configuration file for your scans.

### When to Use Resolutions

Resolutions should be used when it is impossible to solve an issue or a fix is planned for a later time.

The sections below explain how to create resolutions in the `resolutions.yml` file which, if passed as an argument to
the _reporter_, applies to each scan made. If a resolution is project-specific, then add it in the
[.ort.yml](config-file-ort-yml.md) file for the project.

Resolutions are only taken into account by the _reporter_, while the _analyzer_ and _scanner_ ignore them.

## Resolution Basics

A resolution is applied to specific issues or violations via the regular expression specified in the `message` of a
resolution.

To be able to show why a resolution is acceptable, each resolution must include an explanation. The explanation consists
of:

* `reason` -- an identifier selected from a predefined list of options. 
* `comment` -- free text, providing an explanation and optionally a link to further information.

## Resolving Issues

If the ORT results contain issues, the best approach is usually to fix them and run the scan again. However, sometimes
it is not possible, for example if an issue occurs in the license scan of a third-party dependency which cannot be fixed
or updated.

In such situations, you can *resolve* the issue in any future scan by adding a resolution to the `resolutions.yml` to
mark it as acceptable.

The code below shows the structure of an issue resolution in the `resolutions.yml` file:

```yaml
issues:
- message: "A regular expression matching the error message."
  reason: "One of IssueResolutionReason e.g. BUILD_TOOL_ISSUE,CANT_FIX_ISSUE,SCANNER_ISSUE."
  comment: "A comment further explaining why the reason above is acceptable."
```
Where the list of available options for `reason` is defined in
[IssueResolutionReason.kt](../model/src/main/kotlin/config/IssueResolutionReason.kt).

For example, to ignore an issue related to a build tool problem, your `resolutions.yml` could include:

```yaml
issues:
- message: "Does not have X.*"
  reason: "BUILD_TOOL_ISSUE"
  comment: "Error caused by a known issue for which fix is being implemented, see https://github.com/..."
```

## Resolving Policy Rule Violations

Resolutions should not be used to resolve license policy rule violations as they do not the change generated open source
notices. To resolve a license policy rule violation either add a local `license_findings` curation to the
[.ort.yml file](./config-file-ort-yml.md) if the finding is in your code repository or add a curation to the
[curations.yml](config-file-curations-yml.md) if the violation occurs in a third-party dependency.

The code below shows the structure of a policy rule violation resolution in the `resolutions.yml` file:

```yaml
rule_violations:
- message: "A regular expression matching the policy rule violation message."
  reason: "One of RuleViolationResolutionReason e.g. CANT_FIX_EXCEPTION, DYNAMIC_LINKAGE_EXCEPTION."
  comment: "A comment further explaining why the reason above is applicable."
```

Where the list of available options for `reason` is defined in
[RuleViolationResolutionReason.kt](../model/src/main/kotlin/config/RuleViolationResolutionReason.kt).

For example, to confirm your organization has acquired an org-wide Qt commercial license, your `resolutions.yml` could
include:

```yaml
rule_violations:
- message: ".*LicenseRef-scancode-qt-commercial-1.1 found in 'third-party/qt/LICENSE'.*"
  reason: "LICENSE_ACQUIRED_EXCEPTION"
  comment: "Org-wide commercial Qt license was purchased, for details see https://jira.example.com/issues/SOURCING-1234"
```

### Resolving Vulnerabilities

The code below shows the structure of a vulnerability resolution in the `resolutions.yml` file:

```yaml
vulnerabilities:
- id: "A regular expression matching the vulnerability id."
  reason: "One of VulnerabilityResolutionReason e.g. CANT_FIX_VULNERABILITY, INEFFECTIVE_VULNERABILITY."
  comment: "A comment further explaining why the reason above is applicable."
```

Where the list of available options for `reason` is defined in
[VulnerabilityResolutionReason.kt](../model/src/main/kotlin/config/VulnerabilityResolutionReason.kt).

For example, to ignore a vulnerability that is ineffective, because it is not invoked in your project, your
`resolutions.yml` could include:

```yaml
vulnerabilities:
- id: "CVE-9999-9999"
  reason: "INEFFECTIVE_VULNERABILITY"
  comment: "CVE-9999-9999 is a false positive"
```

## Command Line

To use the `resolutions.yml` file put it to `$ORT_CONFIG_DIR/resolutions.yml` or pass it to the `--resolutions-file`
option of the _reporter_:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
  --report-formats NoticeTemplate,StaticHtml,WebApp
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml
  --resolutions-file $ORT_CONFIG_DIR/resolutions.yml
```
