# Package Curations

Curations correct invalid or missing package metadata and set the concluded license for packages.

## The problem

Package metadata is often incomplete or inaccurate because developers may overlook setting or updating values. For example, the source code location (VCS) information might be missing, point to outdated repositories, or reference repositories with incorrectly tagged releases.

This is a significant issue because legal professionals typically emphasize two license compliance challenges:
A) Packages often contain more licenses than those declared in the package metadata
B) The licenses of the code included in the released binary artifact are what legally matters

So, how can we automate license checking within package sources at speed and scale when the metadata is missing or incorrect?

Since it’s not feasible or advisable to correct metadata in already released packages, ORT offers a curation mechanism for package metadata. Curations are YAML files that contain metadata corrections for a specific package or a range of versions. When placed in the `$ORT_CONFIG_DIR` directory, they are automatically used by the [ORT Analyzer](../cli/analyzer.md) to amend the metadata provided by the packages themselves.

By using ORT curations, you can effectively address package metadata issues, enabling automation of license compliance and open source policies while generating high-quality SBOMs.

## When to use

Use a curation to:

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
* set *labels*:
  * Add key-value pairs to the package in order to inject custom per-package data into, for example, policy
    rules, reporter templates, or custom ORT plugins.

The sections below explain how to create curations in the `curations.yml` file which, if [ORT Analyzer](../cli/analyzer.md) is run, are applied to all package metadata found in the analysis.

⚠️  If a license detected in the project's sources needs to be corrected, [add a license finding curation to an .ort.yml file](ort-yml.md#correcting-project-license-findings) within the root of the project's code repository.

## File format

A curation file consists of one or more `id` entries:

```yaml
- id: "An ORT package identifier e.g. Maven:com.example.app:example:0.0.1."
  curations:
    comment: |
      An explanation why the curation is needed or why it is set to specific value.
      It’s recommended to include links to the relevant code or ticket to support your explanation.
    purl: "A package URL e.g. pkg:Maven/com.example.app/example@0.0.1?arch=arm64-v8a#src/main."
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
    labels:
      my-key: "my-value"
```

For the package curation specification, refer to [PackageCurationData.kt][PackageCurationData].

⚠️ If the `concluded_license` *and* the `authors` are curated, this package will be skipped during the `scan` step, as no more information from the scanner is required.  This does require the `skipConcluded` scanner option to be enabled in your [config.yml](index.md#ort-configuration-file) file.

## Command line

To use a `curations.yml` file or a `curations` directory, put it to `$ORT_CONFIG_DIR` directory or pass it via the `--package-curations-file` or `--package-curations-dir` option to the [ORT Evaluator](../cli/evaluator.md). Afterwards run the [ORT Analyzer](../cli/analyzer.md) again to determine the dependencies of projects and their curated metadata.

```shell
cli/build/install/ort/bin/ort analyze \
  -i <source-code-of-project-dir> \
  -o <analyzer-output-dir>
```

File and directory package curation providers may also be configured in `$ORT_CONFIG_DIR/config.yml` as [FilePackageCurationProviders][FilePackageCurationProvider] within the `packageCurationProviders`.

To override curations, e.g. for testing them locally, you can also pass a `curations.yml` file or a curations directory via the `--package-curations-file` or `--package-curations-dir` options of the [ORT Evaluator](../cli/evaluator.md).

```shell
cli/build/install/ort/bin/ort evaluate \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-file $ORT_CONFIG_DIR/curations.yml \
  --package-curations-dir $ORT_CONFIG_DIR/curations \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

Alternatively, you can also use the ORT docker image.

```shell
docker run ghcr.io/oss-review-toolkit/ort evaluate \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-file $ORT_CONFIG_DIR/curations.yml \
  --package-curations-dir $ORT_CONFIG_DIR/curations \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

## Curation providers

Besides a YAML configuration files, ORT also supports the *curation provider plugins* which can retrieve curations from external providers or to auto-curate common issues for specific packages. To learn how to write such plugins see:

* [ClearlyDefined package curation provider plugin][ClearlyDefinedPackageCurationProvider]
* [Spring package curation provider plugin][SpringPackageCurationProvider]

These plugins can be configured in the [config.yml][reference-yml] file within the `packageCurationProviders`.

## Related resources

* Code
  * [model/src/main/kotlin/PackageCurationData][PackageCurationData]
  * [model/src/main/resources/reference.yml][reference-yml]
  * [plugins/package-curation-providers/](https://github.com/oss-review-toolkit/ort/tree/main/plugins/package-curation-providers)
* Examples
  * [examples/curations.yml](https://github.com/oss-review-toolkit/ort/blob/main/examples/curations.yml)
  * [curations/ within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/tree/main/curations)
  * [ClearlyDefined package curation provider plugin][ClearlyDefinedPackageCurationProvider]
  * [Spring package curation provider plugin][SpringPackageCurationProvider]
* How-to guides
  * [How to address tool issues](../../how-to-guides/how-to-address-tool-issues.md)
  * [How to correct copyrights](../../how-to-guides/how-to-correct-copyrights.md)
  * [How to correct licenses](../../how-to-guides/how-to-correct-licenses.md)
  * [How to define package sources](../../how-to-guides/how-to-define-package-sources.md)
* Reference
  * [Evaluator CLI --package-curations-file and --repository-configuration-file options](../cli/evaluator.md#configuration-options)
  * [Helper CLI package-curations commands](../cli/orth.md#commands)

[ClearlyDefinedPackageCurationProvider]: https://github.com/oss-review-toolkit/ort/blob/main/plugins/package-curation-providers/clearly-defined/src/main/kotlin/ClearlyDefinedPackageCurationProvider.kt
[FilePackageCurationProvider]: https://github.com/oss-review-toolkit/ort/blob/main/plugins/package-curation-providers/file/src/main/kotlin/FilePackageCurationProvider.kt
[PackageCurationData]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/PackageCurationData.kt
[reference-yml]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml
[SpringPackageCurationProvider]: https://github.com/oss-review-toolkit/ort/blob/main/plugins/package-curation-providers/spring/src/main/kotlin/SpringPackageCurationProvider.kt
