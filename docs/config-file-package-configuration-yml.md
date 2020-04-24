# The package configuration file (Experimental)

A package configuration sets up path excludes and license finding curations specific to a package (dependency) and 
provenance. It is thus a similar concept to the `.ort.yml` file which allows to do similar configuration for projects as
opposed to packages.

## Specifying the target package and provenance

Each package configuration applies exactly to one package-Id and provenance which must be specified. The provenance
can either specify a *source artifact* or a *VCS location and revision*. Thus in case one scans the *source artifact*
and the *VCS* for each package, then two package configurations would be required in order to address issues with both
scans. For example configurations for `ansi-styles 4.2.1` would specify the following... 
  
* to configure the VCS scan:
```yaml
  id: "NPM::ansi-styles:4.2.1"
  vcs:
    type: "Git"
    url: "https://github.com/chalk/ansi-styles.git"
    revision: "74d421cf32342ac6ec7b507bd903a9e1105f74d7"
```
* to configure the source artifact scan:
```yaml
  id: "Maven:org.ossreviewtoolkit.ort:1.2.3"
  source_artifact_url: "https://registry.npmjs.org/ansi-styles/-/ansi-styles-3.2.1.tgz"
```

## Specifying path excludes and license finding curations

Path excludes are used to tell ORT that license findings in certain files can be ignored, for example because they
belong to the documentation of the package. License finding curations are used to fix wrong scan results, for example
if a wrong license was detected, or if a finding is a false positive. The entries for path excludes and license finding
curations have the same syntax and semantic as the analog entries for the `ort.yml`, see
[Excluding paths](config-file-ort-yml.md#excluding-paths) and
[Curating license findings](config-file-ort-yml.md#curating-license-findings).

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

## Organizing package configurations

ORT currently provides two alternatives for organizing package configurations to choose from. It is required for both
options that there is at most one package configuration provided for any (package, provenance) combination.

### Configuration directory

A directory containing one `.yml` file for each configured (package, provenance) combination.
Each `.yml` file contains exactly one package configuration as shown in the parent sections.
This directory can be specified via the `--package-configuration-dir` option to the *evaluator* and the *reporter*
command respectively.  
 
### Configuration file

A single `.yml` containing one large array with each entry being one package configuration as shown in the parent
section. This file can be specified via the `--package-configuration-file` option to the *evaluator* and the *reporter*
command respectively. For example such single file would look as follows:
 
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
```yaml
