# Package Curations

Curations correct invalid or missing package metadata and set the concluded license for packages.

You can use the [curations.yml example](#example) as the base configuration file for your scans.

## When to Use Curations

Curations can be used to:

* correct invalid or missing package metadata such as:
  * package source code repository.
  * tag or revision (SHA1) for a specific package version.
  * binary or source artifacts.
  * declared license.
  * package description or URL to its homepage.
* set the concluded license for a package:
  * concluded license is the license applicable to a package dependency defined as an SPDX license expression.
* set the *is_metadata_only* flag:
  * metadata-only packages, such as Maven BOM files, do not have any source code.
    Thus, when the flag is set, the *downloader* just skips the download and the *scanner* skips the scan.
    Also, any *evaluator rule* may optionally skip its execution.
* set the *is_modified* flag:
  * it indicates whether files of this package have been modified compared to the original files, e.g., in case of a fork of an upstream Open Source project, or a copy of the code in this project's repository.
* set the *declared_license_mapping* property:
  * Packages may have declared license string values which cannot be parsed to SpdxExpressions.
    In some cases, this can be fixed by mapping these strings to a valid license.
    If multiple curations declare license mappings, they get combined into a single mapping.
    Thus, multiple curations can contribute to the declared license mapping for the package.
    The effect of its application can be seen in the *declared_license_processed* property of the respective curated package.
* set the *source_code_origins* property:
  * Override the source code origins priority configured in the downloader configuration by the given one.
    Possible values: VCS, ARTIFACT.

The sections below explain how to create curations in the `curations.yml` file which, if passed to the *analyzer*, is applied to all package metadata found in the analysis.
If a license detected in the source code of a package needs to be corrected, add a license finding curation in the [.ort.yml](ort-yml.md#curations) file for the project.

## Curations Basics

To discover the source code of the dependencies of a package, ORT relies on the package metadata.
Often the metadata contains information on how to locate the source code, but not always.
In many cases, the metadata of packages provides no VCS information, it points to outdated repositories or the repositories are not correctly tagged.
Because it is not always possible to fix this information in upstream packages, ORT offers a curation mechanism for metadata.

These curations can be configured in a YAML file passed to the *analyzer*.
The data from the curations file amends the metadata provided by the packages themselves.
This way, it is possible to fix broken VCS URLs or provide the location of source artifacts.

Hint:
If the `concluded_license` *and* the `authors` are curated, this package will be skipped during the `scan` step, as no more information from the scanner is required.
This requires the `skipConcluded` scanner option to be enabled in the [config.yml](../getting-started/usage.md#ort-configuration-file).

A curation file consists of one or more `id` entries:

```yaml
- id: "Maven:com.example.app:example:0.0.1"
  curations:
    comment: "An explanation why the curation is needed or the reasoning for a license conclusion"
    purl: "pkg:Maven/com.example.app/example@0.0.1?arch=arm64-v8a#src/main"
    authors:
    - "Name of one author"
    - "Name of another author"
    cpe: "cpe:2.3:a:example-org:example-package:0.0.1:*:*:*:*:*:*:*"
    concluded_license: "Valid SPDX license expression to override the license findings."
    declared_license_mapping:
      "license a": "Apache-2.0"
    description: "Curated description."
    homepage_url: "http://example.com"
    binary_artifact:
      url: "http://example.com/binary.zip"
      hash:
        value: "ddce269a1e3d054cae349621c198dd52"
        algorithm: "MD5"
    source_artifact:
      url: "http://example.com/sources.zip"
      hash:
        value: "ddce269a1e3d054cae349621c198dd52"
        algorithm: "MD5"
    vcs:
      type: "Git"
      url: "http://example.com/repo.git"
      revision: "1234abc"
      path: "subdirectory"
    is_metadata_only: true
    is_modified: true
    source_code_origins: [ARTIFACT, VCS]
```

Where the list of available options for curations is defined in [PackageCurationData.kt](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/PackageCurationData.kt).

## Command Line

To make ORT use the `curations.yml` file, put it to the default location of `$ORT_CONFIG_DIR/curations.yml` and then run the *analyzer*:

```shell
cli/build/install/ort/bin/ort analyze
  -i [source-code-of-project-dir]
  -o [analyzer-output-dir]
```

Alternatively to a single file, curations may also be split across multiple files below a directory, by default `$ORT_CONFIG_DIR/curations`.
File and directory package curation providers may also be configured as [FilePackageCurationProviders](https://github.com/oss-review-toolkit/ort/blob/main/plugins/package-curation-providers/file/src/main/kotlin/FilePackageCurationProvider.kt) in `$ORT_CONFIG_DIR/config.yml`.
Similarly, ORT can use [ClearlyDefined](https://clearlydefined.io/) and [SW360](https://www.eclipse.org/sw360/) as sources for curated metadata.
See the [reference configuration file](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml) for examples.

To override curations, e.g. for testing them locally, you can also pass a `curations.yml` file or a curations directory via the `--package-curations-file` / `--package-curations-dir` options of the *evaluator*:

```shell
cli/build/install/ort/bin/ort evaluate
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir]
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml
  --package-curations-file $ORT_CONFIG_DIR/curations.yml
  --package-curations-dir $ORT_CONFIG_DIR/curations
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

## Example

```mdx-code-block
import CodeBlock from '@theme/CodeBlock';
import Example from '!!raw-loader!@site/../examples/curations.yml'

<CodeBlock language="yml" title="curations.yml">{Example}</CodeBlock>
```
