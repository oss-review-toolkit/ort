# The `curations.yml` file

Curations correct invalid or missing package metadata and set the concluded license for packages.

You can use the [curations.yml example](./examples/curations.yml) as the base configuration file for your scans.

## When to Use Curations

Curations can be used to:

* correct invalid or missing package metadata such as:
  * package source code repository.
  * tag or revision (SHA1) for specific package version.
  * binary or source artifacts.
  * declared license.
  * package description or URL to its homepage.
* set the concluded license for a package:
  * concluded license is the license applicable to a package dependency defined as an SPDX license expression.

The sections below explain how to create curations in the `curations.yml` file which,
if passed to the _analyzer_, is applied to all package metadata found in the analysis.
If a license detected in the source code of a package needs to be corrected, add
a license finding curation in the [.ort.yml](config-file-ort-yml.md#curations) file for the project.

## Curations Basics

In order to discover the source code of the dependencies of a package, ORT relies on the package metadata. Often the
metadata contains information on how to locate the source code, but not always. In many cases, the metadata of packages
provides no VCS information, it points to outdated repositories or the repositories are not correctly tagged.
Because it is not always possible to fix this information in upstream packages, ORT offers a curation mechanism for
metadata.

These curations can be configured in a YAML file that is passed to the _analyzer_. The data from the curations file
amends the metadata provided by the packages themselves. This way, it is possible to fix broken VCS URLs or provide the
location of source artifacts.

The structure of the curations file consist of one or more `id` entries:

```
- id: "package identifier."
  curations:
    comment: "An explanation why the curation is needed or the reasoning for a license conclusion"
    concluded_license: "valid SPDX license expression to override the license findings."
    declared_licenses:
    - "license a"
    - "license b"
    description: "Curated description"
    homepage_url: "http://example.com"
    binary_artifact:
      url: "http://example.com/binary.zip"
      hash: "ddce269a1e3d054cae349621c198dd52"
      hash_algorithm: "MD5"
    source_artifact:
      url: "http://example.com/sources.zip"
      hash: "ddce269a1e3d054cae349621c198dd52"
      hash_algorithm: "MD5"
    vcs:
      type: "git"
      url: "http://example.com/repo.git"
      revision: "1234abc"
      path: "subdirectory"
````

## Command Line

To use the `curations.yml` file pass it to the `--package-curations-file` option of the _analyzer_:

```
cli/build/install/ort/bin/ort analyze
  -i [source-code-of-project-path]
  -o [analyzer-output-path]
  --package-curations-file [ort-configuration-path]/curations.yml
```

In the future we will integrate [ClearlyDefined](https://clearlydefined.io/) as a source for curated metadata. Until
then, and also for curations for organization internal packages which are not publicly available, the curations file can be used.

To test curations you can also pass the `curations.yml` file to the `--package-curations-file` option of the
_evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate
  -i [scanner-output-path]/scan-result.yml
  -o [evaluator-output-path]
  --output-formats YAML
  --license-configuration-file [ort-configuration-path]/licenses.yml
  --package-curations-file [ort-configuration-path]/curations.yml
  --rules-file [ort-configuration-path]/rules.kts
```
