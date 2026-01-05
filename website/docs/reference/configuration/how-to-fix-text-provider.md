# How to Fix Text Provider

The `reporter.how-to-fix-text-provider.kts` file enables the injection of how-to-fix texts in Markdown format for ORT issues into the reports.

## File format

The `reporter.how-to-fix-text-provider.kts` file uses a Kotlin-based DSL, see below [related resources](#related-resources) for details.

## Command line

To use a `*.how-to-fix-text-provider.kts` file, put it to `$ORT_CONFIG_DIR` directory or pass it via the `--how-to-fix-text-provider-script` option to the [ORT Reporter](../cli/reporter.md).

```shell
$ cli/build/install/ort/bin/ort report \
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats CycloneDX,PlainTextTemplate,SpdxDocument,WebApp \
  --how-to-fix-text-provider-script example.how-to-fix-text-provider.kts
```

Alternatively, you can also use the ORT docker image.

```shell
docker run ghcr.io/oss-review-toolkit/ort report \
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats CycloneDX,PlainTextTemplate,SpdxDocument,WebApp \
  --how-to-fix-text-provider-script example.how-to-fix-text-provider.kts
```

## Related resources

* Code
  * [reporter/src/main/kotlin/HowToFixTextProvider.kt](https://github.com/oss-review-toolkit/ort/blob/main/reporter/src/main/kotlin/HowToFixTextProvider.kt)
* Examples
  * [examples/example.how-to-fix-text-provider.kts](https://github.com/oss-review-toolkit/ort/blob/main/examples/example.how-to-fix-text-provider.kts)
* How-to guides
  * [How to address tool issues](../../how-to-guides/how-to-address-tool-issues.md)
* Reference
  * [Reporter CLI](../cli/reporter.md)
