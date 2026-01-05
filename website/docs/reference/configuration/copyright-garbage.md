# Copyright Garbage

The `copyright-garbage.yml` file allows you to define which copyright statements should be considered garbage and thus removed as invalid findings from the scanner.

## File format

Removal of invalid copyright findings can be done by specifying using literal strings in `items` or regular expression patterns in `patterns`.

```yaml
---
items:
- "<string matching invalid copyright finding>"
- "<string matching invalid copyright finding>"
patterns:
- "<regular expression matching invalid copyright finding>"
- "<regular expression matching invalid copyright finding>"
```

## Command line

Either create a file at the default location at `$ORT_CONFIG_DIR/copyright-garbage.yml`, or pass a custom file via the `--copyright-garbage-file` option of the [ORT Evaluator](../cli/evaluator.md) or [ORT Reporter](../cli/reporter.md).

```shell
cli/build/install/ort/bin/ort evaluate
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-dir $ORT_CONFIG_DIR/curations \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts \
  --copyright-garbage-file $ORT_CONFIG_DIR/copyright-garbage.yml
```

```shell
cli/build/install/ort/bin/ort report
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats CycloneDX,PlainTextTemplate,SpdxDocument,WebApp \
  --copyright-garbage-file $ORT_CONFIG_DIR/copyright-garbage.yml
```

Alternatively, you can also use the ORT docker image.

```shell
docker run ghcr.io/oss-review-toolkit/ort report \
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats CycloneDX,PlainTextTemplate,SpdxDocument,WebApp \
  --copyright-garbage-file $ORT_CONFIG_DIR/copyright-garbage.yml
```

## Related resources

* Code
  * [model/src/main/kotlin/config/CopyrightGarbage.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/CopyrightGarbage.kt)
* Examples
  * [examples/copyright-garbage.yml](https://github.com/oss-review-toolkit/ort/blob/main/examples/copyright-garbage.yml)
  * [copyright-garbage.yml within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/blob/main/copyright-garbage.yml)
* How-to guides
  * [How to correct licenses](../../how-to-guides/how-to-correct-licenses.md)
* Reference
  * [Helper CLI --input-copyright-garbage-file and --output-copyright-garbage-file commands](../cli/orth.md#commands)
