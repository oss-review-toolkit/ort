# Evaluator Rules

The `evaluator.rules.kts` file allows you to define custom policy rules that automatically apply to review scan findings.
Rules are written in a Kotlin-based DSL.

For each policy rule violation, you can define 'How to fix' follow-up actions to help users resolve policy rules violations by themselves.

You can use the [example rules](#example) as the base script file for your policy rules.
Note that this example depends on the license categorizations defined in the [license-classifications example](license-classifications.md#example).

## Command Line

To use a `*.rules.kts` file, put it to `$ORT_CONFIG_DIR/evaluator.rules.kts` or pass it via the `--rules-file` option to the *evaluator*:

```shell
cli/build/install/ort/bin/ort evaluate \
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir] \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-file $ORT_CONFIG_DIR/curations.yml  \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

## Example

```mdx-code-block
import CodeBlock from '@theme/CodeBlock';
import Example from '!!raw-loader!@site/../examples/example.rules.kts'

<CodeBlock language="kotlin" title="example.rules.kts">{Example}</CodeBlock>
```
