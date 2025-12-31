# Copyright Garbage

The `copyright-garbage.yml` file allows defining which Copyright statements are to be considered as garbage, like any invalid findings from a scanner.
This can be done by literal strings or regular expression patterns.
The *evaluator* and *reporter* take the file as optional input.
See the [example](#example) as a base to get started.

## Command Line

Either create a file at the default location at `$ORT_CONFIG_DIR/copyright-garbage.yml`, or pass a custom file via the `--copyright-garbage-file` option of the *evaluator* or *reporter*.
For example:

```shell
cli/build/install/ort/bin/ort report
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
  --report-formats PlainTextTemplate,StaticHtml,WebApp
  --copyright-garbage-file $ORT_CONFIG_DIR/copyright-garbage.yml
```

## Example

```mdx-code-block
import CodeBlock from '@theme/CodeBlock';
import Example from '!!raw-loader!@site/../examples/copyright-garbage.yml'

<CodeBlock language="yml" title="copyright-garbage.yml">{Example}</CodeBlock>
```
