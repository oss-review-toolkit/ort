# ORT CLI Reference

The command line reference for the various ORT tools.

## Usage

```
ort [<options>] <command> [<args>]
```

## Options

* `-c`, `--config=<value>` - The path to a configuration file. (default: ~/.ort/config/config.yml)
* `--error`, `--warn`, `--info`, `--debug` - Set the verbosity level of log output. (default: WARN)
* `--stacktrace` - Print out the stacktrace for all exceptions.
* `-P=<value>` - Override a key-value pair in the configuration file.
* `--help-all` - Display help for all subcommands.
* `--generate-completion=(bash|zsh|fish)`.
* `-v`, `--version` - Show the version and exit.
* `-h`, `--help` - Show this message and exit.

## Commands

* `advise` - Check dependencies for security vulnerabilities.
* `analyze` - Determine dependencies of a software project.
* `compare` - Compare two ORT results with various methods.
* `config` - Show different ORT configurations.
* `download` - Fetch source code from a remote location.
* `evaluate` - Evaluate ORT result files against policy rules.
* `migrate` - Assist with migrating ORT configuration to newer ORT versions.
* `notify` - Create notifications based on an ORT result.
* `plugins` - Print information about the installed ORT plugins.
* `report` - Present Analyzer, Scanner and Evaluator results in various formats.
* `requirements` - Check for the command line tools required by ORT.
* `scan` - Run external license/copyright scanners.
* `upload-curations` - Upload ORT package curations to ClearlyDefined.
* `upload-result-to-postgres` - Upload an ORT result to a PostgreSQL database.

## Related resources

* Code
  * [cli/src/main/kotlin/OrtMain.kt](https://github.com/oss-review-toolkit/ort/blob/main/cli/src/main/kotlin/OrtMain.kt)
