# ORT Helper CLI Reference

The command line reference for the ORT Helper, also known as `orth`.
ORT helper can be found in either:

* `ort/helper-cli/build/install/orth/bin/` if you build ORT from source.
* `/opt/ort/bin/` yf you are using the ORT Docker image.

## Usage

```
orth [<options>] <command> [<args>]...
```

## Options

* `--error`, `--warn`, `--info`, `--debug` - Set the verbosity level of log output. (default: **WARN**)
* `--stacktrace` - Print out the stacktrace for all exceptions.
* `-h`, `--help` - Show this message and exit.

## Commands

* `--convert-ort-file` - Converts the given ORT file to a different format, e.g., '.json' to '.yml'.
* `--create-analyzer-result` - Creates an analyzer result that contains packages for the given list of package ids. The result contains only packages which have a corresponding ScanCode scan result in the PostgreSQL storage.
* `--create-analyzer-result-from-package-list` - A command which turns a package list file into an analyzer result.
* `--dev` - Commands for development.
* `--extract-repository-configuration` - Extract the repository configuration from the given ORT result file.
* `--generate-timeout-error-resolutions` - Generates resolutions for scanner timeout errors. The result is written to the standard output.
* `--get-package-licenses` - Shows the root license and the detected license for a package denoted by the given package identifier.
* `--download-results-from-postgres` - Download an ORT result from a PostgreSQL database. The symmetric command to ORT's upload-result-to-postgres command.
* `--import-copyright-garbage` - Import copyright garbage from a plain text file containing one copyright statement per line into the given copyright garbage file.
* `--import-scan-results` - Import all scan results from the given ORT result file to the file-based scan results storage directory.
* `--license-classifications` - Commands for working with license classifications.
* `--list-copyrights` - Lists the copyright findings.
* `--list-license-categories` - Lists the license categories.
* `--list-licenses` - Lists the license findings for a given package as distinct text locations.
* `--list-packages` - Lists the packages and optionally also projects contained in the given ORT result file.
* `--list-stored-scan-results` - Lists the provenance of all stored scan results for a given package identifier.
* `--map-copyrights` - Reads processed copyright statements from the input file, maps them to unprocessed copyright statements using the given ORT file, and writes those mapped statements to the given output file.
* `--merge-repository-configurations` - Merges the given list of input repository configuration files and writes the result to the given output repository configuration file.
* `--package-configuration` - Commands for working with package configurations.
* `--package-curations` - Commands for working with package curations.
* `--provenance-storage` - Commands for working with provenance storages.
* `--repository-configuration` - Commands for working with package configurations.
* `--set-dependency-representation` - Set the dependency representation of an ORT result to a specific target format.
* `--set-labels` - Set the labels in an ORT result file.
* `--transform` - Implements a JSLT transformation on the given ORT result file.
* `--verify-source-artifact-curations` - Verifies that all curated source artifacts can be downloaded and that the hashes are correct.

## Related resources

* Code
  * [cli-helper/src/main/kotlin/utils/OrtHelperCommand.kt](https://github.com/oss-review-toolkit/ort/blob/main/cli-helper/src/main/kotlin/utils/OrtHelperCommand.kt)
* How-to guides
  * [How to add non-detected or supported packages](../../how-to-guides/how-to-add-non-detected-or-supported-packages.md)
