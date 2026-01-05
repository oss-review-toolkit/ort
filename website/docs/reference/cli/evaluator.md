# Evaluator  CLI Reference

The *evaluator* is used to perform custom license policy checks on scan results.
The rules to check against are implemented as Kotlin scripts with a dedicated DSL.

## Usage

```
ort evaluate [<options>]
```

## Input Options

* `-i`, `--ort-file=<value>` - The ORT result file to read as input.

## Output Options

* `-o`, `--output-dir=<value>` - The directory to write the ORT result file with evaluation results to. If no output directory is specified, no ORT result file is written and only the exit code signals a success or failure.
* `-f`, `--output-formats=(JSON|YAML)` - The list of output formats to be used for the ORT result file(s). (default: [YAML])

## Configuration Options

* `--copyright-garbage-file=<value>` - A file containing copyright statements which are marked as garbage. (default: ~/.ort/config/copyright-garbage.yml)
* `--license-classifications-file=<value>` - A file containing the license classifications which are passed as a parameter to the rules script. (default: ~/.ort/config/license-classifications.yml)
* `--package-configurations-dir=<value>` - A directory that is searched recursively for package configuration files. Each file must only contain a single package configuration.
* `--package-curations-file=<value>` - A file containing package curations. This replaces all package curations contained in the given ORT result file with the ones present in the given file and, if enabled, those from the repository configuration.
* `--package-curations-dir=<value>` - A directory containing package curation files. This replaces all package curations contained in the given ORT result file with the ones present in the given directory and, if enabled, those from the repository configuration.
* `--repository-configuration-file=<value>` - A file containing the repository configuration. If set, overrides the repository configuration contained in the ORT result input file.
* `--resolutions-file=<value>` - A file containing issue and rule violation resolutions. (default: ~/.ort/config/resolutions.yml)

## Options

* `-r`, `--rules-file=<value>` - The name of a script file containing rules.
* `--rules-resource=<text>` - The name of a script resource on the classpath that contains rules. See [Evaluator Rules][evaluator-rules] for the file format
* `-l`, `--label=<value>` - Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple times. For example: `--label distribution=external`.
* `--check-syntax` - Do not evaluate the script but only check its syntax. No output is written in this case.
* `-h`, `--help` - Show this message and exit.

## Related resources

* Code
  * [plugins/commands/evaluator/src/main/kotlin/EvaluateCommand.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/commands/evaluator/src/main/kotlin/EvaluateCommand.kt)
* How-to guides
  * [How to classify licenses](../../how-to-guides/how-to-classify-licenses.md)
  * [How to make a license choice](../../how-to-guides/how-to-make-a-license-choice.md)
  * [How to address a license policy violation](../../how-to-guides/how-to-address-a-license-policy-violation.md)
* Reference
  * [Evaluator Rules][evaluator-rules]
* Tutorials
  * [Running policy checks](../../tutorials/walkthrough/running-policy-checks.md)
  * [Automate your Policy Checks](../../tutorials/automating-policy-checks.md)

[evaluator-rules]:../configuration/evaluator-rules.md
