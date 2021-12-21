# The `curations.yml` file

Curations correct invalid or missing package metadata and set the concluded license for packages.

You can use the [curations.yml example](../examples/curations.yml) as the base configuration file for your scans.

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
* set the _is_meta_data_only_ flag:
  * metadata-only packages, such as Maven BOM files, do not have any source code. Thus, when the flag is set the
  _downloader_ just skips the download and the _scanner_ skips the scan. Also, any _evaluator rule_ may optionally skip
  its execution.
* set the _is_modified_ flag:
  * indicates whether files of this package have been modified compared to the original files, e.g., in case of a fork
    of an upstream Open Source project, or a copy of the code in this project's repository. 
* set the _declared_license_mapping_ property:
  * Packages may have declared license string values which cannot be parsed to SpdxExpressions. In some cases this can
    be fixed by mapping these strings to a valid license. If multiple curations declare license mappings, they get
    combined into a single mapping. Thus, multiple curations can contribute to the declared license mapping for the
    package. The effect of its application can be seen in the _declared_license_processed_ property of the respective
    curated package. 

The sections below explain how to create curations in the `curations.yml` file which,
if passed to the _analyzer_, is applied to all package metadata found in the analysis.
If a license detected in the source code of a package needs to be corrected, add
a license finding curation in the [.ort.yml](config-file-ort-yml.md#curations) file for the project.

## Curations Basics

In order to discover the source code of the dependencies of a package, ORT relies on the package metadata. Often the
metadata contains information on how to locate the source code, but not always. In many cases, the metadata of packages
provides no VCS information, it points to outdated repositories or the repositories are not correctly tagged. Because it
is not always possible to fix this information in upstream packages, ORT offers a curation mechanism for metadata.

These curations can be configured in a YAML file that is passed to the _analyzer_. The data from the curations file
amends the metadata provided by the packages themselves. This way, it is possible to fix broken VCS URLs or provide the
location of source artifacts.

The structure of the curations file consist of one or more `id` entries:

```yaml
- id: "Maven:com.example.app:example:0.0.1"
  curations:
    comment: "An explanation why the curation is needed or the reasoning for a license conclusion"
    purl: "pkg:Maven/com.example.app/example@0.0.1?arch=arm64-v8a#src/main"
    cpe: "cpe:2.3:a:example-org:example-package:0.0.1:*:*:*:*:*:*:*"
    concluded_license: "Valid SPDX license expression to override the license findings."
    declared_license_mapping:
      "license a": "Apache-2.0"
    description: "Curated description."
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
      type: "Git"
      url: "http://example.com/repo.git"
      revision: "1234abc"
      path: "subdirectory"
    is_meta_data_only: true
    is_modified: true
````
Where the list of available options for curations is defined in
[PackageCurationData.kt](../model/src/main/kotlin/PackageCurationData.kt).

## Command Line

To use the `curations.yml` file put it to `$ORT_CONFIG_DIR/curations.yml` or pass it to the `--package-curations-file`
option of the _analyzer_:

```bash
cli/build/install/ort/bin/ort analyze
  -i [source-code-of-project-dir]
  -o [analyzer-output-dir]
  --package-curations-file $ORT_CONFIG_DIR/curations.yml
```

Alternatively specify a directory with multiple curation files using the `--package-curations-dir` to the _analyzer_:

```bash
cli/build/install/ort/bin/ort analyze
  -i [source-code-of-project-dir]
  -o [analyzer-output-dir]
  --package-curations-dir $ORT_CONFIG_DIR/curations
``` 

ORT can use [ClearlyDefined](https://clearlydefined.io/) as a source for curated metadata. The preferred workflow is to
use curations from ClearlyDefined, and to submit curations there. However, this is not always possible, for example in
case of curations for organization internal packages. To support this workflow, ClearlyDefined can be enabled as the
single source for curations or in combination with a `curations.yml` with the `--clearly-defined-curations` option of
the analyzer:  

```bash
cli/build/install/ort/bin/ort analyze
  -i [source-code-of-project-dir]
  -o [analyzer-output-dir]
  --package-curations-file $ORT_CONFIG_DIR/curations.yml
  --clearly-defined-curations
```

To test curations you can also pass the `curations.yml` file to the `--package-curations-file` option of the
_evaluator_:

```bash
cli/build/install/ort/bin/ort evaluate
  -i [scanner-output-dir]/scan-result.yml
  -o [evaluator-output-dir]
  --output-formats YAML
  --license-classifications-file $ORT_CONFIG_DIR/license-classifications.yml
  --package-curations-file $ORT_CONFIG_DIR/curations.yml
  --rules-file $ORT_CONFIG_DIR/evaluator.rules.kts
```
