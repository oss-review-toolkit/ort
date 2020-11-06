# The `license-classifications.yml` file

The `license-classifications.yml` file holds a user-defined categorization of licenses.

You can use the [license-classifications.yml example](../examples/license-classifications.yml) as the base configuration
file for your scans.

### When to Use

Set *include\_in_notice_file* if the license does not require attribution. Similarly, setting
*include_source_code_offer_in_notice_file* to `true` will ensure a written source code offer is included in the notices.
Licenses can be assigned to license sets like "permissive" or "public domain" which can be used by the
[rules](file-rules-kts.md) to determine how to handle the license.

## Command Line

To use the `license-classifications.yml` file put it to `$ORT_CONFIG_DIR/license-classifications.yml` or pass it to the
`--license-configuration-file` option of the _evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir]
  --output-formats YAML
  --license-configuration-file $ORT_CONFIG_DIR/license-classifications.yml
  --package-curations-file $ORT_CONFIG_DIR/curations.yml
  --rules-file $ORT_CONFIG_DIR/rules.kts
```
