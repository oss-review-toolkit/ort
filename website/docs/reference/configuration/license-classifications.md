# License Classifications

The `license-classifications.yml` file holds a user-defined categorization of licenses.

## When to use

The information from the `license-classifications.yml` can be used by the:

* ORT Evaluator: By defining categories like "permissive" or "public domain", policy rules can determine how to handle specific licenses and throw an error, warning or hint policy violations if incompliance is detected.
* ORT Reporter: Based on their associated categories, the [plain text templates](reporter-templates.md#generate-plain-text-attribution-notices) for generating NOTICE files can decide which licenses to include in the generated notice file. Use the `include-in-notice-file` category to mark a license as requiring attribution and include it in generated NOTICE files. Similary, if you want to include a written source code offer for a license, mark it with `include-source-code-offer-in-notice-file`.

Users can choose their own license classifications and define their own policy rules and notices templates to achieve desired results. ORT allows complete flexibility in category semantics, meaning licenses can be assigned to multiple, overlapping categories with different interpretations.

## File format

The `categories` section of the `license-classifications.yml` file allows you to define arbitrary categories for grouping licenses. Each category consists of a **name** and an optional **description**; the names must be unique.

After defining the `categories`, use the `categorizations` section to assign licenses to specific categories. Licenses are identified using [SPDX identifiers or SPDX expressions](https://spdx.dev/learn/handling-license-info/). Each license can be assigned to multiple categories by listing the names of those categories. Note that only names that reference the categories defined in the first section are valid.

```yaml
---
categories:
- name: "<string defining category>"
  description: "<string describing category, ideally including rationale>"
- name: "include-in-notice-file"
- name: "include-source-code-offer-in-notice-file"
categorizations:
- id: "<SPDX license expression>"
  categories:
  - "<string matching one of the names in categories>"
```

## Command line

To use the `license-classifications.yml` file put it to `$ORT_CONFIG_DIR` directory or pass it to the `--license-classifications-file` option of to the [ORT Evaluator](../cli/evaluator.md).

```shell
cli/build/install/ort/bin/ort evaluate \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-file $ORT_CONFIG_DIR/curations.yml \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

Alternatively, you can also use the ORT docker image.

```shell
docker run ghcr.io/oss-review-toolkit/ort evaluate \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-file $ORT_CONFIG_DIR/curations.yml \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

## Related resources

* Code
  * [model/src/main/kotlin/licenses/LicenseClassifications.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/licenses/LicenseClassifications.kt)
* Examples
  * [examples/license-classifications.yml](https://github.com/oss-review-toolkit/ort/blob/main/examples/license-classifications.yml)
  * [license-classifications.yml within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/blob/main/license-classifications.yml)
  * [generated license-classifications.yml within the LDBcollector project](https://github.com/maxhbr/LDBcollector/blob/generated/ort/license-classifications.yml)
* How-to guides
  * [How to classify licenses](../../how-to-guides/how-to-classify-licenses.md)
* JSON schema
  * [integrations/schemas/license-classifications-schema.json](https://github.com/oss-review-toolkit/ort/blob/main/integrations/schemas/license-classifications-schema.json)
* Reference
  * [Downloader CLI --license-classifications-file option](../cli/downloader.md#configuration-options)
  * [Evaluator CLI --license-classifications-file option](../cli/evaluator.md#configuration-options)
  * [Helper CLI --license-classifications command](../cli/orth.md#commands)
  * [Reporter CLI --license-classifications-file option](../cli/reporter.md#configuration-options)
