# Package Configurations

A package configuration file allows you to define path excludes and license finding curations for a specific package (dependency) and provenance.
Conceptually, the file is similar to [.ort.yml](ort-yml.md), but it is used only for packages included via a package manager as project dependencies, and not for the project's own source code repository to be scanned.

## The problem

In your software project, the dependencies typically consist of (binary) release artifacts. However, to achieve high levels of license compliance, scanning the package sources is essential, leading to two main challenges:
A) No license scanner can guarantee 100% accuracy; manual license corrections are often still necessary.
B) Package sources usually contain more files and directories than those found in the (binary) release artifacts. To minimize the effort required for manual corrections due to point A, any license or vulnerability findings for files not present in release artifacts can be considered non-applicable.

Ideally, your build tool or package manager would provide a way to identify exactly which files in the package sources are included in the release artifacts. Unfortunately, most do not, or they don’t do so in a cost-effective manner.

To address these challenges, ORT offers package configurations to correct license findings from the scanner or to exclude findings for files not present in release artifacts. Package configurations are YAML files that, when placed in the `$ORT_CONFIG_DIR` directory, are automatically used by the [ORT Evaluator](../cli/evaluator.md) to check findings in packages against policy and by the [ORT Reporter](../cli/reporter.md) for visualizing scanner file findings.

By using ORT package configurations, you can highly automate software supply chain compliance and generate high-quality SBOMs while reducing manual effort.

## When to use

Use a package configuration to:

* Mark files and directories in package sources as not included the corresponding (binary) released artifacts.
  Use it to make clear that license findings in documentation or tests in a package sources do not apply to the release (binary) artifact which is a dependency in your project.
* Overwrite scanner findings to correct identified licenses for a specific file(s) present in a dependency sources or code repository.

## Package configuration file basics

A package configuration applies to the packages it matches with.
It contains the mandatory `id` matcher, for matching package IDs, which allows for using [Ivy-style version matchers][ivy-style-version-matchers].
In addition to the `id`, at most one of the matchers `vcs`, `sourceArtifactUrl` or `sourceCodeOrigin` may additionally
be specified, which targets the repository provenance, the source artifact provenance or just the source code origin of
the package's scan result(s).

The following example illustrates a package configuration for `ansi-styles 4.2.1`, utilizing the available options:

```yaml
# Apply only specified source artifact by its URL.
id: "NPM::ansi-styles:4.2.1"
source_artifact_url: "https://registry.npmjs.org/ansi-styles/-/ansi-styles-4.2.1.tgz"

# Apply only to specific code repository URL with optionally a hash.
id: "NPM::ansi-styles:4.2.1"
vcs:
  type: "Git"
  url: "https://github.com/chalk/ansi-styles.git"
  revision: "74d421cf32342ac6ec7b507bd903a9e1105f74d7"

# Apply to all versions lower than 4.2.1 where a code repository was scanned.
id: "NPM::ansi-styles:(,4.2.1]"
source_code_origin: VCS

# Apply to versions all versions greater or equal to 4.0
# and lower or equal to 4.2.1 where a source artifact was scanned.
id: "NPM::ansi-styles:[4.0,4.2.1]"
source_code_origin: ARTIFACT

# Apply only to version 4.2.1, regardless whether
# code repository or source artifact was scanned.
id: "NPM::ansi-styles:4.2.1"
```

## File format

Path excludes define which code is not part of the distributed release artifact(s) for a package, for example, code found in the source repository but only used for building, documenting or testing the code.
License finding curations are used to fix incorrect scan results, for example, if a wrong license was detected, or if a finding is a false positive.

The entries for path excludes and license finding curations have the same syntax and semantics as in the `ort.yml` file, see [excluding paths](ort-yml.md#excludes-paths) and [curating license findings](ort-yml.md#correcting-project-license-findings) for details.

```yaml
id: "An ORT package identifier e.g. Pip::example-package:0.0.1."
path_excludes:
- pattern: "A glob pattern matching file or directory paths."
  reason: >
    One of PathExcludeReason e.g.
    BUILD_TOOL_OF,
    DATA_FILE_OF,
    DOCUMENTATION_OF,
    EXAMPLE_OF,
    OPTIONAL_COMPONENT_OF,
    OTHER,
    PROVIDED_BY,
    TEST_OF or
    TEST_TOOL_OF.
  comment: "A comment further explaining why the path is excluded."
license_finding_curations:
- path: "A glob pattern matching files or paths."
  start_lines: "3"
  line_count: 11
  detected_license: "SPDX license expression"
  reason: >
    One of LicenseFindingCurationReason e.g.
    CODE,
    DATA_OF,
    DOCUMENTATION_OF,
    INCORRECT,
    NOT_DETECTED or
    REFERENCE.
  comment: |
    An explanation why the license finding curation is needed or why it is set to specific value.
    It’s recommended to include links to the relevant code or ticket to support your explanation.
  concluded_license: "SPDX license expression"
```

Refer to [PackageConfiguration.kt][PackageConfiguration] ror the package configuration specification.

To learn how to write glob patterns, consult the [AntPathMatcher documentation][AntPathMatcher]. Also, check [PathExcludeReason.kt][PathExcludeReason] and [LicenseFindingCurationReason.kt][LicenseFindingCurationReason] for available `reason` options and their usage.

## Command line

ORT consumes package configuration from a so-called "package configuration directory" which is searched recursively for `.yml` files.
Each such file must contain exactly one package configuration, and there must not be more than one package configuration for any package/provenance combination within that directory. The default location is `$ORT_CONFIG_DIR/package-configurations/`.

To use a custom location, you can pass it to the `--package-configurations-dir` option of the [ORT Evaluator](../cli/evaluator.md):

```shell
cli/build/install/ort/bin/ort evaluate \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-file $ORT_CONFIG_DIR/curations.yml \
  --package-configurations-dir $ORT_CONFIG_DIR/packages \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

Or to the [ORT Reporter](../cli/reporter.md):

```shell
cli/build/install/ort/bin/ort evaluate \
  -i <evaluator-output-dir>/evaluation-result.yml \
  -o <reporter-output-dir> \
  --report-formats PlainTextTemplate,WebApp \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-configurations-dir $ORT_CONFIG_DIR/packages
```

Alternatively, you can also use the ORT docker image.

```shell
docker run ghcr.io/oss-review-toolkit/ort report \
  -i <scanner-output-dir>/scan-result.yml \
  -o <evaluator-output-dir> \
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml \
  --package-curations-file $ORT_CONFIG_DIR/curations.yml \
  --package-configurations-dir $ORT_CONFIG_DIR/packages \
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

## Related resources

* Code
  * [main/model/src/main/kotlin/config/PackageConfiguration.kt][PackageConfiguration]
  * [model/src/main/resources/reference.yml][reference-yml]
  * [plugins/package-configuration-providers/](https://github.com/oss-review-toolkit/ort/tree/main/plugins/package-configuration-providers)
* Examples
  * [examples/package-configurations.ort.yml](https://github.com/oss-review-toolkit/ort/blob/main/examples/package-configurations.ort.yml)
  * [package-configurations/ within the ort-config repository](https://github.com/oss-review-toolkit/ort-config/tree/main/package-configurations)
  * [Ivy-style version matchers][ivy-style-version-matchers]
* How-to guides
  * [How to address tool issues](../../how-to-guides/how-to-address-tool-issues.md)
  * [How to exclude dirs, files or scopes](../../how-to-guides/how-to-exclude-dirs-files-or-scopes)
  * [How to correct licenses](../../how-to-guides/how-to-correct-licenses.md)
* Reference
  * [Evaluator CLI --package-configurations-dir option](../cli/evaluator.md#configuration-options)
  * [Helper CLI package-configuration commands](../cli/orth.md#commands)
  * [Reporter CLI --package-configurations-dir option](../cli/reporter.md#configuration-options)

[AntPathMatcher]: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html
[ivy-style-version-matchers]: https://ant.apache.org/ivy/history/2.5.0/settings/version-matchers.html
[LicenseFindingCurationReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/LicenseFindingCurationReason.kt
[PackageConfiguration]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/PackageConfiguration.kt
[PathExcludeReason]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/PathExcludeReason.kt
[reference-yml]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml
