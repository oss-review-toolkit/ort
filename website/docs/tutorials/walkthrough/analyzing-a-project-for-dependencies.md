# Analyzing a Project for Dependencies

This is part of the [ORT walkthrough tutorial](index.md). Make sure you've completed the setup steps before continuing.

The [Analyzer] is ORT's first step. It automatically detects build files in your project (like `Cargo.toml`, `package.json`, or `pom.xml`) and queries package managers to build a complete dependency tree. You'll see how ORT identifies dependencies and captures metadata like licenses, authors, and source locations that later steps use for scanning and compliance checks.

The output is an `analyzer-result.yml` file containing your project's complete dependency tree along with metadata about each package.

## Running the Analyzer

Make sure you're in the `ort-walkthrough` directory, then run:

```shell
docker run --rm \
  -v "$(pwd)/todo_list_rust":/workspace \
  -v "$(pwd)/ort-config":/home/ort/.ort/config \
  -v "$(pwd)/ort-output":/ort-output \
  ghcr.io/oss-review-toolkit/ort:76.0.0 \
   -P ort.forceOverwrite=true \
  analyze \
    --input-dir /workspace \
    --output-dir /ort-output
```

Here's what each part does:

| Option | Description |
| ------ | ----------- |
| `-v "$(pwd)/todo_list_rust":/workspace` | Mounts the project directory into the container |
| `-v "$(pwd)/ort-config":/home/ort/.ort/config` | Mounts the ORT configuration directory |
| `-v "$(pwd)/ort-output":/ort-output` | Mounts the output directory |
| `ghcr.io/oss-review-toolkit/ort:76.0.0` | The ORT Docker image |
| -P ort.forceOverwrite=true | Overwrite any existing files in output directory |
| `analyze` | The ORT command to run |
| `--input-dir /workspace` | The project directory to analyze |
| `--output-dir /ort-output` | Where to write results |

For all available options, see the [Analyzer CLI reference][analyzer].

## Understanding the output

You should see output similar to this:

```
Looking for ORT configuration in the following file:
        /home/ort/.ort/config/config.yml (does not exist)

Looking for analyzer-specific configuration in the following files and directories:
        /workspace/.ort.yml (does not exist)
        /home/ort/.ort/config/resolutions.yml
The following 27 package manager(s) are enabled:
        Bazel, Bower, Bundler, Cargo, Carthage, CocoaPods, Composer, Conan, Gleam, GoMod, Gradle Inspector, Maven, NPM, NuGet, PIP, Pipenv, PNPM, Poetry, Pub, SBT, SpdxDocumentFile, Stack, Swift Package Manager, Tycho, Unmanaged, Yarn, Yarn 2+
The following 3 package curation provider(s) are enabled:
        DefaultDir, DefaultFile, Spring
Analyzing project path:
        /workspace
Found 1 Cargo definition file(s) at:
        Cargo.toml
Found in total 1 definition file(s) from the following 1 package manager(s):
        Cargo
Wrote analyzer result to '/ort-output/analyzer-result.yml' (0.02 MiB) in 178.367ms.
The analysis took 1.447250084s.
Found 1 project(s) and 11 package(s) in total (not counting excluded ones).
Applied 0 curation(s) from 0 of 3 provider(s).
Resolved issues: 0 errors, 0 warnings, 0 hints.
Unresolved issues: 0 errors, 0 warnings, 0 hints.
```

Key takeaways from this output:

* ORT detected a `Cargo.toml` file and used the **Cargo** plugin to analyze it
* It found **1 project** (our todo_list application) and **11 packages** (dependencies)
* There were **no issues** - the analysis completed successfully

## Understanding the result file

Open `ort-output/analyzer-result.yml`. This file is the foundation for all subsequent ORT steps.
The sections below explain the various parts of this file.

### Repository information

```yaml
repository:
  vcs:
    type: "Git"
    url: "https://github.com/alalfakawma/todo_list_rust"
    revision: "304cad54f510782634c8c14e941a72c1079bcff7"
    path: ""
```

ORT detected that we're in a Git repository and recorded the URL and commit.

### Project information

```yaml
analyzer:
  result:
    projects:
    - id: "Cargo::todo_list:1.2.5"
      definition_file_path: "Cargo.toml"
      authors:
      - "alalfakawma"
      declared_licenses:
      - "MIT"
      declared_licenses_processed:
        spdx_expression: "MIT"
      scope_names:
      - "dependencies"
```

This shows our project: a Cargo project called `todo_list` version 1.2.5, licensed under MIT.

### Package information

Each dependency is listed with its metadata:

```yaml
    packages:
    - id: "Crate::serde:1.0.111"
      purl: "pkg:cargo/serde@1.0.111"
      authors:
      - "David Tolnay"
      - "Erick Tryzelaar"
      declared_licenses:
      - "MIT OR Apache-2.0"
      declared_licenses_processed:
        spdx_expression: "Apache-2.0 OR MIT"
      description: "A generic serialization/deserialization framework"
      homepage_url: "https://serde.rs"
      source_artifact:
        url: "https://crates.io/api/v1/crates/serde/1.0.111/download"
        hash:
          value: "c9124df5b40cbd380080b2cc6ab894c040a3070d995f5c9dc77e18c34a8ae37d"
          algorithm: "SHA-256"
      vcs:
        type: "Git"
        url: "https://github.com/serde-rs/serde.git"
```

For each package, ORT captures for its metadata:

* **Identifier** (`id`) - A unique identifier in the format `Type::Name:Version`
* **Package URL** (`purl`) - A [standardized package identifier][purl]
* **Authors** - Who (legally) wrote the package (not always the same as the people who contributed to the package)
* **Declared licenses** - What declared license(s) are
* **Source artifact** - Where to download the source code artifact
* **VCS information** - What the location is of the code source repository (possibly includes tag or filepath)

This metadata is used by subsequent ORT steps to download sources, scan for copyrights/licenses, and check for vulnerabilities.

## What's next

The Analyzer produced a complete snapshot of the project's dependencies as a large YAML file. For a friendlier view, let's continue to [visualizing results](visualizing-results.md) where we convert the analyzer output into an easy-to-navigate HTML report.

## Related resources

* How-to guides
  * [How to exclude dirs, files, or scopes](../../how-to-guides/how-to-exclude-dirs-files-or-scopes.md)
* Reference
  * [Analyzer CLI][analyzer]
  * [Package curations - correct package metadata](../../reference/configuration/package-curations.md)

[analyzer]: ../../reference/cli/analyzer.md
[purl]: https://github.com/package-url/purl-spec
