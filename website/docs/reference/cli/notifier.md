# Notifier CLI Reference

The command line reference for the ORT Notifier which can be used to create notifications based on an ORT result.

## Usage

```
ort notify [<options>]
```

## Input Options

* `-i`, `--ort-file=<value>` - The ORT result file to read as input.
* `-n`, `--notifications-file=<value>` - The name of a Kotlin script file containing notification rules.

## Configuration Options

* `--resolutions-file=<value>` - A file containing issue and rule violation resolutions. (default: **/Users/tsteenbe/.ort/config/resolutions.yml**)

## Options

* `-l`, `--label=<value>` - Set a label in the ORT result passed to the notifier script, overwriting any existing label of the same name. Can be used multiple times. For example: `--label distribution=external`.
* `-h`, `--help` - Show this message and exit.

## Related resources

* Code
  * [plugins/commands/notifier/src/main/kotlin/NotifyCommand.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/commands/notifier/src/main/kotlin/NotifyCommand.kt)
