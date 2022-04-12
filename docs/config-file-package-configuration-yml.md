# The Package Configuration File

A package configuration file allows you to define path excludes and license finding curations for a specific package
(dependency) and provenance. Conceptually, the file is similar to
[.ort.yml](https://github.com/oss-review-toolkit/ort/blob/main/docs/config-file-ort-yml.md), but it is used only for
packages included via a package manager as project dependencies, and not for the project's own source code repository to
be scanned.

### When To Use
Use a package configuration file to:

- mark files and directories as not included in released artifacts -- use it to make clear that license findings in
  documentation or tests in a package sources do not apply to the release (binary) artifact which is a dependency in
  your project.
- overwrite scanner findings to correct identified licenses in a dependency for a specific file(s).

# Package Configuration File Basics

Each package configuration applies exactly to one *package id* and *provenance* which must be specified. The
*provenance* can be specified as either a *source artifact* or a *VCS location* with an optional revision.

Here is an example of a package configuration for `ansi-styles 4.2.1`, when the source artifact is (to be) scanned:

```yaml
  id: "NPM::ansi-styles:4.2.1"
  source_artifact_url: "https://registry.npmjs.org/ansi-styles/-/ansi-styles-4.2.1.tgz"
```

If the source repository is (to be) scanned, then use the package configuration below:

```yaml
  id: "NPM::ansi-styles:4.2.1"
  vcs:
    type: "Git"
    url: "https://github.com/chalk/ansi-styles.git"
    revision: "74d421cf32342ac6ec7b507bd903a9e1105f74d7"
```

## Defining Path Excludes and License Finding Curations

Path excludes define which code is not part of the distributed release artifact(s) for a package, for example code found
in the source repository but only used for building, documenting or testing the code. License finding curations are used
to fix incorrect scan results, for example if a wrong license was detected, or if a finding is a false positive.

The entries for path excludes and license finding curations have the same syntax and semantics as in the `ort.yml` file,
see [excluding paths](config-file-ort-yml.md#excluding-paths) and
[curating license findings](config-file-ort-yml.md#curating-project-license-findings) for details.

```yaml
  id: "Pip::example-package:0.0.1"
  source_artifact_url: "https://some-host/some-file-path.tgz"
  path_excludes:
  - pattern: "docs/**"
    reason: "DOCUMENTATION_OF"
    comment: "This directory contains documentation which is not distributed."
  license_finding_curations:
  - path: "src/**.cpp"
    start_lines: "3"
    line_count: 11
    detected_license: "GPL-2.0-only"
    reason: "CODE"
    comment: "The scanner matches a variable named `gpl`."
    concluded_license: "Apache-2.0"
```

## Command Line

ORT offers two different ways to use package configurations:

- A single configuration `.yml` containing an array with each entry defining the configuration for one package.
- A directory with configuration files with one file for each configured package/provenance combination.

Note that in both of the above options only one package configuration can exist for a package/provenance combination.

### Using a Package Configuration Directory

By default, ORT uses a directory with configuration files for each *package id* located at
`$ORT_CONFIG_DIR/package-configurations/`. To use a custom location you can pass it to the `--package-configuration-dir`
option of the _evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir]
  --output-formats YAML
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml
  --package-curations-file $ORT_CONFIG_DIR/curations.yml
  --package-configuration-dir $ORT_CONFIG_DIR/packages
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

or to the _reporter_:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
  --report-formats NoticeTemplate,WebApp
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml
  --package-configuration-dir $ORT_CONFIG_DIR/packages
```
 
### Using a Single Package Configuration File

To use a single package configuration `.yml` file, pass it to the `--package-configuration-file` option of the
_evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir]
  --output-formats YAML
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml
  --package-curations-file $ORT_CONFIG_DIR/curations.yml
  --package-configuration-file $ORT_CONFIG_DIR/packages.yml
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```

or to the _reporter_:

```bash
cli/build/install/ort/bin/ort report
  -i [evaluator-output-dir]/evaluation-result.yml
  -o [reporter-output-dir]
  --report-formats NoticeTemplate,WebApp
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml
  --package-configuration-file $ORT_CONFIG_DIR/packages.yml
```

The code below shows an example for `packages.yml`:

```yaml
- id: "Pip::example-package:0.0.1"
  source_artifact_url: "https://some-host/some-file-path.tgz"
  path_excludes:
  - pattern: "docs/**"
    reason: "DOCUMENTATION_OF"
    comment: "This directory contains documentation which is not distributed."
  license_finding_curations:
  - path: "src/**.cpp"
    start_lines: "3"
    line_count: 11
    detected_license: "GPL-2.0-only"
    reason: "CODE"
    comment: "The scanner matches a variable named `gpl`."
    concluded_license: "Apache-2.0"
- id: "Pip::example-package:0.0.2"
  source_artifact_url: "https://some-host/some-other-file-path.tgz"
  path_excludes:
  - pattern: "docs/**"
    reason: "DOCUMENTATION_OF"
    comment: "This directory contains documentation which is not distributed."
  license_finding_curations:
  - path: "src/**.cpp"
    start_lines: "3"
    line_count: 11
    detected_license: "GPL-2.0-only"
    reason: "CODE"
    comment: "The scanner matches a variable named `gpl`."
    concluded_license: "Apache-2.0"
```
