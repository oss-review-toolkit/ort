# How to pass external information to ORT

You can pass external information into ORT for several practical purposes, for example:

* Use business, delivery, or organizational attributes inside ORT Evaluator policy rules.
* Mark packages as proprietary (no sources available), or tag them as coming from a specific vendor or your organization.

## Setting labels for an ORT run

Pass key or key-value labels to [Analyzer], [Scanner], [Evaluator], and [Notifier] with `-l` or `--label`:

```
cli/build/install/ort/bin/ort analyze \
  -i <project-dir> \
  -o <analyzer-output-dir> \
  -l dist=external \
  -l org=engineering-sdk-xyz-team-germany-berlin \
```

## Setting labels for an package found by ORT

Use a package [curation] to set a label for specific package version or range.

```
- id: "NPM:example-webapp:0.0.1."
  curations:
    comment: |
      Packages does not define namespace, setting label to mark it being from our organization.
    labels:
      org: "my-org-example"
```

```
- id: "Maven:com.example.vendor.lib:api-client"
  curations:
    comment: |
      Package is a fat JAR or uber-JAR meaning its dependencies are included within JAR.
      See <link to artifact>.
    labels:
      artifact-type: "fat-jar"
      bundles-deps: "true"
```

## Using labels within Evaluator policy rules

In the Evaluator policy rules e.g. `evaluator.rules.kts`  use `hasLabel()` on rules
or OrtResult.hasLabel() in helper functions.

```
fun RuleSet.missingNameInDependencyRule() = packageRule("UNAPPROVED_ORG_PROJECT_LICENSE") {
    require {
        +isProject()
        +packageManagerSupportsDeclaredLicenses()
        -hasLabel("sw-type", "oss-project")
    }

    licenseRule("UNAPPROVED_ORG_PROJECT_LICENSE", LicenseView.ONLY_DECLARED) {
        require {
            -isApprovedOrgProjectLicense()
        }

        error(
            "Package '${pkg.metadata.id.toCoordinates()}' declares $license which is not an " +
                    "approved license for closed sourced $orgName software.",
            howToFixOssProjectDefault()
        )
    }
}
```

```
fun getEnabledPolicyRules(): PolicyRules =
    when {
        ortResult.hasLabel("sw-type", "oss-project") -> PolicyRules.OSS_PROJECT
        else -> PolicyRules.PROPRIETARY_PROJECT
    }
```

## Related resources

* Code
  * [model/src/main/kotlin/OrtResult.kt labels, getLabelValues() and hasLabel()](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/OrtResult.kt)
  * [evaluator/src/main/kotlin/Rule.kt hasLabel()](https://github.com/oss-review-toolkit/ort/blob/main/evaluator/src/main/kotlin/Rule.kt)
* Examples
  * [Using labels in the GitHub Action for ORT](https://github.com/oss-review-toolkit/ort-ci-github-action?tab=readme-ov-file#Run-ORT-with-labels)
  * [Using labels in the Forgejo Action for ORT](https://github.com/oss-review-toolkit/ort-ci-forgejo-action#run-ort-with-labels)
  * [Using labels in the GitLab Job Template for ORT](https://github.com/oss-review-toolkit/ort-ci-gitlab?tab=readme-ov-file#Run-ORT-with-labels)
  * [Labels with evaluator.rules.kts within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/blob/main/evaluator.rules.kts)
* Reference
  * [ORT Analyzer CLI -l option](../reference/cli/evaluator.md#options)
  * [ORT Evaluator CLI -l option](../reference/cli/evaluator.md#options)
  * [ORT Helper CLI --set-labels command](../reference/cli/orth.md#commands)
  * [ORT Notifier CLI -l option](../reference/cli/notifier.md#options)
  * [ORT Scanner CLI -l option](../reference/cli/scanner.md#options)

[analyzer]: ../reference/cli/analyzer.md
[curation]: ../reference/configuration/package-curations.md
[evaluator]: ../reference/cli/evaluator.md
[scanner]: ../reference/cli/scanner.md
