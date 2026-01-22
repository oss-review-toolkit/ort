# Advisor CLI Reference

The *ORT Advisor* retrieves security advisories from configured services.
It requires the analyzer result as an input.
For all the packages identified by the analyzer, it queries the services configured for known security vulnerabilities.
The vulnerabilities returned by these services are then stored in the output result file together with additional information like the source of the data and a severity (if available).

## Usage

```
ort advise [<options>]
```

## Output options

* `-o`, `--output-dir=<value>` - The directory to write the ORT result file with advisor results to.
* `-f`, `--output-formats=(JSON|YAML)` - The list of output formats to be used for the ORT result file(s). (default: YAML)

## Configuration options

* `--resolutions-file=<value>` - A file containing issue and rule violation resolutions. (default: ~/.ort/config/resolutions.yml)

## Options

* `-i`, `--ort-file=<value>` - An ORT result file with an analyzer result to use.
* `-l`, `--label=<value>` - Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple times. For example: `--label distribution=external`.
* `-a`, `--advisors=<value>` - The comma-separated advisors to use, any of [BlackDuck, OSSIndex, OSV, VulnerableCode].
* `--skip-excluded` - Do not check excluded projects or packages. (deprecated)
* `-h`, `--help` - Show this message and exit.

## Advisors configuration file formats

Multiple providers for security advisories are available. These providers require specific configuration in the [ORT configuration file (e.g., config.yml)][reference-yml], which should be placed in the *advisor* section.

When running the advisor, you can select the providers to enable using the `--advisors` option (or its shorthand `-a`), expecting a comma-separated list of provider IDs. The following sections details supported the providers and their configurations.

### Black Duck

This vulnerability provider obtains information about security vulnerabilities from the Black Duck instance specified in the configuration.
The configuration is mandatory, because authentication is required.

⚠️ The implementation of Black Duck vulnerability provider is in *experimental* state.

Initial experiments indicate that it works with the ecosystems mentioned [over here](https://github.com/oss-review-toolkit/ort/issues/9638).

```yaml
ort:
  advisor:
    config:
      BlackDuck:
        options:
          serverUrl: 'server-url'
        secrets:
          apiToken: 'token'
```

To enable this provider, run the *ORT Advisor* with the `-a BlackDuck` option.

### OSS Index

This vulnerability provider does not require any further configuration as it uses the public service at https://ossindex.sonatype.org/.
Before using this provider, please ensure to comply with its [Terms of Service](https://ossindex.sonatype.org/tos).

To enable this provider, run the *ORT Advisor* with the `-a OssIndex` option.

### OSV

This provider obtains information about security vulnerabilities from Google [OSV](https://osv.dev/), a distributed vulnerability database for Open Source.
The database aggregates data from different sources for various ecosystems.
The configuration is optional and limited to overriding the server URL.

```yaml
ort:
  advisor:
    config:
      osv:
        options:
          serverUrl: "https://api-staging.osv.dev"
```

To enable this provider, run the *ORT Advisor* with the `-a OSV` option.

### VulnerableCode

This provider obtains information about security vulnerabilities from a [VulnerableCode](https://github.com/aboutcode-org/vulnerablecode) instance.
The configuration is limited to the server URL, as authentication is not required:

```yaml
ort:
  advisor:
    config:
      vulnerableCode:
        options:
          serverUrl: "http://localhost:8000"
```

To enable this provider, run the *ORT Advisor* with the `-a VulnerableCode` option.

## Related resources

* Code
  * [plugins/commands/advisor/src/main/kotlin/AdviseCommand.kt](https://github.com/oss-review-toolkit/ort/blob/main/plugins/commands/advisor/src/main/kotlin/AdviseCommand.kt)
  * [plugins/advisors/black-duck](https://github.com/oss-review-toolkit/ort/tree/main/plugins/advisors/black-duck)
  * [plugins/advisors/oss-index](https://github.com/oss-review-toolkit/ort/tree/main/plugins/advisors/oss-index)
  * [plugins/advisors/osv](https://github.com/oss-review-toolkit/ort/tree/main/plugins/advisors/osv)
  * [plugins/advisors/vulnerable-code](https://github.com/oss-review-toolkit/ort/tree/main/plugins/advisors/vulnerable-code)
* How-to guides
  * [How to check and remediate vulnerabilities in dependencies](../../how-to-guides/how-to-check-and-remediate-vulnerabilities-in-dependencies.md)

[reference-yml]: https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml
