# CI Integrations

ORT can be integrated into CI pipelines to automate license compliance checks and SBOM generation. Running ORT in CI ensures that every change to your project or project release is automatically scanned for license compliance issues and security vulnerabilities.

## GitHub Actions

The [GitHub Action for ORT][ort-ci-github-action] runs ORT in GitHub workflows. It supports all ORT tools ([Analyzer], [Scanner], [Advisor], [Evaluator], [Reporter]) and can be configured to fail builds on policy violations. See this [repository](https://github.com/oss-review-toolkit/ort-ci-github-action) for configuration options and usage examples.

```yaml
name: ORT

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  ort:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout project
        uses: actions/checkout@v4
      - name: Run ORT
        uses: oss-review-toolkit/ort-ci-github-action@v1
        with:
          run: >-
            analyzer,
            advisor,
            evaluator,
            reporter
          fail-on: 'violations'
          report-formats: >-
            CycloneDX,
            SpdxDocument
          ort-cli-report-args: >-
            -O CycloneDX=output.file.formats=json,xml
            -O SpdxDocument=outputFileFormats=JSON,YAML
```

## GitLab CI

The [GitLab CI template][ort-ci-gitlab] provides a reusable job for running ORT in GitLab pipelines. It requires GitLab 15 or higher. See this [repository][ort-ci-gitlab] for configuration options and usage examples.

```yaml
include:
  - https://raw.githubusercontent.com/oss-review-toolkit/ort-ci-gitlab/main/templates/ort-scan.yml

stages:
  - ort

ort-scan:
  stage: ort
  extends: .ort-scan
  variables:
    SW_NAME: 'My Project'
    SW_VERSION: '1.0.0'
    ALLOW_DYNAMIC_VERSIONS: 'true'
    RUN: 'analyzer,advisor,evaluator,reporter'
    FAIL_ON: 'violations'
  artifacts:
    when: always
    paths:
      - $ORT_RESULTS_PATH
```

## Forgejo Actions

The [Forgejo Action for ORT][ort-ci-forgejo-action] runs ORT in Forgejo workflows -
see its [repository][ort-ci-forgejo-action] for configuration options and usage examples.

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  ort:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout project
        uses: actions/checkout@v4
      - name: Run ORT
        uses: https://codeberg.org/oss-review-toolkit/ort-ci-forgejo-action@v1
        with:
          run: >-
            analyzer,
            advisor,
            evaluator,
            reporter
          fail-on: 'violations'
          sw-name: 'My Project'
          sw-version: '1.0.0'
```

## Jenkins

This [Jenkinsfile][ort-ci-jenkins] provides a declarative pipeline for running ORT on Jenkins. It executes the analyzer, scanner, and reporter, and accepts parameters that are translated to ORT command line arguments. See the [Jenkinsfile][ort-ci-jenkins] for documentation of required Jenkins plugins and available parameters.

## ORT configuration

The [GitHub][ort-ci-github-action], [GitLab][ort-ci-gitlab], and [Forgejo actions][ort-ci-forgejo-action] will automatically download configuration file from the [ort-config repository](https://github.com/oss-review-toolkit/ort-config) if no configuration is present. This community-maintained repository provides default configurations including curations, license classifications, and resolutions.

To customize the configuration, you can either:

* Place your own configuration files in `$HOME/.ort/config` in your CI environment
* Clone the default ort-config repository and modify it to suit your needs

For an example of this pattern, see how the Elixir project uses the default ort-config repository while providing its own package configurations and evaluator rules in its [ORT workflow](https://github.com/elixir-lang/elixir/blob/main/.github/workflows/ort/action.yml).

[ort-ci-forgejo-action]: https://codeberg.org/oss-review-toolkit/ort-ci-forgejo-action
[ort-ci-github-action]: https://github.com/oss-review-toolkit/ort-ci-github-action
[ort-ci-gitlab]: https://github.com/oss-review-toolkit/ort-ci-gitlab
[ort-ci-jenkins]: https://github.com/oss-review-toolkit/ort/blob/main/integrations/jenkins/Jenkinsfile
