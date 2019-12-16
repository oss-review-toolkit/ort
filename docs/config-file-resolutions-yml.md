# The `resolutions.yml` file

Resolutions allow you to *resolve* errors or policy rule violations
by providing a reason why they are acceptable and can be ignored.

You can use [examples/resolutions.yml](examples/resolutions.yml) as the base configuration file for your scans.

### When to Use Resolutions
Resolutions should be used when it is impossible to solve an issue or a fix is planned for a later time.

The sections below explain how to create resolutions in the `resolutions.yml` file
which, if passed as an argument to the _reporter_, applies to each scan made. If a resolution is project-specific,
then add it in the [.ort.yml](config-file-ort-yml.md) file for the project.

Resolutions are only taken into account by the _reporter_, while the _analyzer_ and `scanner` ignore them.

## Resolution Basics

A resolution is applied to specific errors or violations via the regular expression specified
in the `message` of a resolution.

To be able to show why a resolution is acceptable, each resolution must include an explanation. 
The explanation consists of:

* `reason` -- an identifier selected from a predefined list of options. 
* `comment` -- free text, providing an explanation and optionally a link to further information.

## Resolving Errors

If the ORT results contain errors, the best approach is usually to fix them and run the scan again. 
However, sometimes it is not possible, for example if an error occurs in the license scan
of a third-party dependency which cannot be fixed or updated.

In such situations, you can *resolve* the error in any future scan by adding a resolution
to the `resolutions.yml` to mark it as acceptable.

The code below shows the structure of an error resolution in the `resolutions.yml` file:

```yaml
errors:
- message: "A regular expression matching the error message."
  reason: "One of ErrorResolutionReason e.g. BUILD_TOOL_ISSUE,CANT_FIX_ISSUE,SCANNER_ISSUE."
  comment: "A comment further explaining why the reason above is acceptable."
```
Where the list of available options for `reason` is defined in
[ErrorResolutionReason.kt](../model/src/main/kotlin/config/ErrorResolutionReason.kt)

For example, to ignore an error related to a build tool problem, your `resolutions.yml` could include:

```yaml
errors:
- message: "Does not have X.*"
  reason: "BUILD_TOOL_ISSUE"
  comment: "Error caused by a known issue for which fix is being implemented, see https://github.com/..."
```

## Resolving Policy Rule Violations

Resolutions should not be used to resolve license policy rule violations as they do not
the change generated open source notices.
To resolve a license policy rule violation either add a local `license_findings` curation
to the [.ort.yml file](./config-file-ort-yml.md) if the finding is in your code repository or add a curation to the [curations.yml](config-file-curations-yml.md) if the violation occurs in a third-party dependency.

The code below shows the structure of a policy rule violation resolution in the `resolutions.yml`file:

```yaml
rule_violations:
- message: "A regular expression matching the policy rule violation message."
  reason: "One of RuleViolationResolutionReason e.g. CANT_FIX_EXCEPTION, DYNAMIC_LINKAGE_EXCEPTION."
  comment: "A comment further explaining why the reason above is applicable."
```

Where the list of available options for `reason` is defined in [RuleViolationResolutionReason.kt](../model/src/main/kotlin/config/RuleViolationResolutionReason.kt)

For example, to confirm your organization has acquired an org-wide Qt commercial license, your `resolutions.yml` could include:

```yaml
rule_violations:
- message: ".*LicenseRef-scancode-qt-commercial-1.1 found in 'third-party/qt/LICENSE'.*"
  reason: "LICENSE_ACQUIRED_EXCEPTION"
  comment: "Org-wide commercial Qt license was purchased, for details see https://jira.example.com/issues/SOURCING-1234"
```

## Command Line

To use the `resolutions.yml` file pass it to the `--resolutions-file` option of the _reporter_:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-path]/evaluation-result.yml
  -o [reporter-output-path]
  --report-formats NoticeByPackage,StaticHtml,WebApp
  --license-configuration-file [ort-configuration-path]/licenses.yml
```
