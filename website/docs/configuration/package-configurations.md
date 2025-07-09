# Package Configurations

A package configuration file allows you to define path excludes and license finding curations for a specific package (dependency) and provenance.
Conceptually, the file is similar to [.ort.yml](ort-yml.md), but it is used only for packages included via a package manager as project dependencies, and not for the project's own source code repository to be scanned.

## When To Use

Use a package configuration file to:

* Mark files and directories as not included in released artifacts.
  Use it to make clear that license findings in documentation or tests in a package sources do not apply to the release (binary) artifact which is a dependency in your project.
* Overwrite scanner findings to correct identified licenses in a dependency for a specific file(s).

## Package Configuration File Basics

A package configuration applies to the packages it matches with.
It contains the mandatory `id` matcher, for matching package IDs, which allows for using [Ivy-style version matchers](https://ant.apache.org/ivy/history/2.5.0/settings/version-matchers.html).
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

## Defining Path Excludes and License Finding Curations

Path excludes define which code is not part of the distributed release artifact(s) for a package, for example, code found in the source repository but only used for building, documenting or testing the code.
License finding curations are used to fix incorrect scan results, for example, if a wrong license was detected, or if a finding is a false positive.

The entries for path excludes and license finding curations have the same syntax and semantics as in the `ort.yml` file, see [excluding paths](ort-yml.md#excluding-paths) and [curating license findings](ort-yml.md#curating-project-license-findings) for details.

```yaml
id: "Pip::example-package:0.0.1"
source_artifact_url: "https://some-host/some-file-path.tgz"
path_excludes:
- pattern: "docs/**"
  reason: "DOCUMENTATION_OF"
  comment: "This directory contains documentation which is not distributed."
license_finding_curations:
- path: "src/**/*.cpp"
  start_lines: "3"
  line_count: 11
  detected_license: "GPL-2.0-only"
  reason: "CODE"
  comment: "The scanner matches a variable named `gpl`."
  concluded_license: "Apache-2.0"
```

## Command Line

ORT consumes package configuration from a so-called "package configuration directory" which is searched recursively for `.yml` files.
Each such file must contain exactly one package configuration, and there must not be more than one package configuration for any package/provenance combination within that directory.
The default location is `$ORT_CONFIG_DIR/package-configurations/`.
To use a custom location, you can pass it to the `--package-configurations-dir` option of the *evaluator*:

```shell
cli/build/install/ort/bin/ort evaluate
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir]
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml
  --package-curations-file $ORT_CONFIG_DIR/curations.yml
  --package-configurations-dir $ORT_CONFIG_DIR/packages
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

Or to the *reporter*:

```shell
cli/build/install/ort/bin/ort report
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
  --report-formats PlainTextTemplate,WebApp
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml
  --package-configurations-dir $ORT_CONFIG_DIR/packages
```
