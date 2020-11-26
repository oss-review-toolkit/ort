# The `rules.kts` file

The `rules.kts` file  allows you to define custom policy rules that automatically apply to review scan findings. Rules
are written in a Kotlin-based DSL.

For each policy rule violation, you can define 'How to fix' follow-up actions to help users resolve policy rules
violations by themselves.

You can use the [rules.kts example](../examples/rules.kts) as the base script file for your policy rules. Note that this
example depends on the licenses categorizations defined in the 
[license-classifications.yml example](../examples/license-classifications.yml), see the
[license-classifications.yml docs](config-file-licenses-yml.md).

## Command Line

To use the `rules.kts` file put it to `$ORT_CONFIG_DIR/rules.kts` or pass it to the `--rules-file` option of the
_evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate \
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir] \
  --output-formats YAML \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-file $ORT_CONFIG_DIR/curations.yml  \
  --rules-file $ORT_CONFIG_DIR/rules.kts
```
