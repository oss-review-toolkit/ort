# How to correct licenses

When ORT's scanner detects incorrect licenses (false positives, misidentified licenses), you can correct them using curations.

## Correcting license findings in your project

To correct a license finding in your project's source code, add a [license finding curation][ort-yml-license-curations] to your `.ort.yml` file:

```yaml
curations:
  license_findings:
  - path: "src/**/*.cpp"
    detected_license: "GPL-2.0-only"
    reason: "INCORRECT"
    comment: "Scanner false positive on variable named 'gpl'."
    concluded_license: "Apache-2.0"
```

## Correcting license findings in a dependency

To correct a license finding in a dependency's source code, create a [package configuration][package-configurations] file:

```yaml
id: "NPM::example-lib:1.0.0"
license_finding_curations:
- path: "src/utils.js"
  detected_license: "GPL-3.0-only"
  reason: "INCORRECT"
  comment: "Scanner matched 'licensed under GPL' in a code comment discussing license compatibility."
  concluded_license: "MIT"
```

## Setting a concluded license for a package

To override all license findings for a package, use a [package curation][package-curations] with `concluded_license`:

```yaml
- id: "Maven:com.example:library:1.0.0"
  curations:
    comment: "Set concluded license based on manual review."
    concluded_license: "Apache-2.0"
```

## Related resources

* Reference
  * [Repository configuration (.ort.yml)][ort-yml]
  * [Package configurations][package-configurations]
  * [Package curations][package-curations]

[ort-yml]: ../reference/configuration/ort-yml.md
[ort-yml-license-curations]: ../reference/configuration/ort-yml.md#correcting-project-license-findings
[package-configurations]: ../reference/configuration/package-configurations.md
[package-curations]: ../reference/configuration/package-curations.md
