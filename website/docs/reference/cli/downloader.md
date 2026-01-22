# Downloader  CLI Reference

By providing an ORT result file with [ORT Analyzer] results as input (`-i`), the *ORT Downloader* retrieves the source code for all included packages and stores it in the specified output directory (`-o`). It handles tasks such as normalizing URLs and using the [appropriate VCS tool](https://github.com/oss-review-toolkit/ort/blob/main/plugins/version-control-systems) to check out source code from version control.

Currently, the following Version Control Systems (VCS) are supported:

* [Git](https://git-scm.com/)
* [Git-Repo](https://source.android.com/setup/develop/repo)
* [Mercurial](https://www.mercurial-scm.org/)
* [Subversion](https://subversion.apache.org/)

## Usage

```
ort download [<options>]
```

## Input Options

* `-i`, `--ort-file=<value>` - An ORT result file with an analyzer result to use. Must not be used together with `--project-url`.
* `--project-url=<value>` - A VCS or archive URL of a project to download. Must not be used together with `--ort-file`.
* `--project-name=<text>` - The speaking name of the project to download. For use together with `--project-url`. Ignored if `--ort-file` is also specified. (default: the last part of the project URL).
* `--vcs-type=<text>` - The VCS type if `--project-url` points to a VCS. Ignored if `--ort-file` is also specified. (default: the VCS type detected by querying the project URL).
* `--vcs-revision=<text>` - The VCS revision if `--project-url` points to a VCS. Ignored if `--ort-file` is also specified. (default: the VCS's default revision).
* `--vcs-path=<text>` - The VCS path to limit the checkout to if `--project-url` points to a VCS. Ignored if `--ort-file` is also specified. (default: no limitation, i.e., the root path is checked out).

## Configuration Options

* `--license-classifications-file=<value>` - A file containing the license classifications that are used to limit downloads if the included categories are specified in the 'config.yml' file. If not specified, all packages are downloaded. (default: ~/.ort/config/license-classifications.yml)

## Output Options

* `-o`, `--output-dir=<value>` - The output directory to download the source code to.

## Options

* `--archive` - Archive the downloaded source code as ZIP files to the output directory. Is ignored if `--project-url` is also specified.
* `--archive-all` - Archive all the downloaded source code as a single ZIP file to the output directory. Is ignored if `--project-url` is also specified.
* `--package-types=(PACKAGE|PROJECT)` - A comma-separated list of the package types from the ORT file's analyzer result to limit downloads to. (default: [PACKAGE, PROJECT])
* `--package-ids=<text>` - A comma-separated list of regular expressions for matching package ids from the ORT file's analyzer result to limit downloads to. If not specified, all packages are downloaded.
* `--skip-excluded` - Do not download excluded projects or packages. Works only with the `--ort-file` parameter. (deprecated)
* `--dry-run` - Do not actually download anything but just verify that all source code locations are valid.
* `-p`, `--max-parallel-downloads=<int>` - The maximum number of parallel downloads to happen. (default: 8)
* `-h`, `--help` - Show this message and exit.

## Related resources

* Code
  * [plugins/commands/downloader/src/main/kotlin/DownloadCommand.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/commands/downloader/src/main/kotlin/DownloadCommand.kt)
* How-to guides
  * [How to download sources for projects and dependencies](../../how-to-guides/how-to-download-sources-for-projects-and-dependencies.md)
  * [How to define package sources](../../how-to-guides/how-to-define-package-sources.md)
  * [How to handle dependencies without sources](../../how-to-guides/how-to-handle-dependencies-without-sources.md)
  * [How to address tool issues](../../how-to-guides/how-to-address-tool-issues.md)
[ORT Analyzer]: ../cli/analyzer.md
