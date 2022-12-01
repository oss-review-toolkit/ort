# The `evaluator.rules.kts` file

The `evaluator.rules.kts` file  allows you to define custom policy rules that automatically apply to review scan
findings. Rules are written in a Kotlin-based DSL.

For each policy rule violation, you can define 'How to fix' follow-up actions to help users resolve policy rules
violations by themselves.

You can use the [example.rules.kts](../examples/evaluator-rules/src/main/resources/example.rules.kts) as the base script
file for your policy rules. Note that this example depends on the licenses categorizations defined in the
[license-classifications.yml example](../examples/license-classifications.yml), see the
[license-classifications.yml docs](config-file-license-classifications-yml.md).

## Command Line

To use the `evaluator.rules.kts` file put it to `$ORT_CONFIG_DIR/evaluator.rules.kts` or pass it to the `--rules-file`
option of the _evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate \
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir] \
  --output-formats YAML \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-file $ORT_CONFIG_DIR/curations.yml  \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```
