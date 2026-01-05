# How to classify licenses

Use [license classifications][license-classifications] to group licenses into categories for policy rules and NOTICE file generation.

## Defining license categories

Create a `license-classifications.yml` file with your categories:

```yaml
categories:
- name: "permissive"
  description: "Licenses with minimal restrictions on use and redistribution."
- name: "copyleft"
  description: "Licenses requiring derivative works to use the same license."
- name: "include-in-notice-file"
  description: "Licenses requiring attribution in NOTICE files."
```

## Assigning licenses to categories

Add licenses to categories using SPDX identifiers:

```yaml
categories:
- name: "permissive"
- name: "copyleft"
- name: "include-in-notice-file"

categorizations:
- id: "Apache-2.0"
  categories:
  - "permissive"
  - "include-in-notice-file"
- id: "MIT"
  categories:
  - "permissive"
  - "include-in-notice-file"
- id: "GPL-3.0-only"
  categories:
  - "copyleft"
  - "include-in-notice-file"
```

## Using categories in evaluator rules

Reference your categories in `evaluator.rules.kts` to enforce policies:

```kotlin
val permissiveLicenses = getLicensesForCategory("permissive")
val copyleftLicenses = getLicensesForCategory("copyleft")
```

## Including licenses in NOTICE files

ORT's built-in NOTICE templates filter licenses by category. Add licenses to the `include-in-notice-file` category to include them in generated NOTICE files:

```yaml
categorizations:
- id: "Apache-2.0"
  categories:
  - "include-in-notice-file"
```

Similarly, use `include-source-code-offer-in-notice-file` to include a written source code offer for copyleft licenses.

## Related resources

* Examples
  * [ort-config license-classifications.yml](https://github.com/oss-review-toolkit/ort-config/blob/main/license-classifications.yml)
  * [ort-config evaluator.rules.kts](https://github.com/oss-review-toolkit/ort-config/blob/main/evaluator.rules.kts)
* Reference
  * [License classifications][license-classifications]

[license-classifications]: ../reference/configuration/license-classifications.md
