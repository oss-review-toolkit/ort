---
sidebar_position: 2
---

# Advisor

The *advisor* retrieves security advisories from configured services.
It requires the analyzer result as an input.
For all the packages identified by the analyzer, it queries the services configured for known security vulnerabilities.
The vulnerabilities returned by these services are then stored in the output result file together with additional information like the source of the data and a severity (if available).

Multiple providers for security advisories are available.
The providers require specific configuration in the [ORT configuration file](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/resources/reference.yml), which needs to be placed in the *advisor* section.
When executing the advisor, the providers to enable are selected with the `--advisors` option (or its short alias `-a`); here a comma-separated list with provider IDs is expected.
The following sections describe the providers supported by the advisor:

## Black Duck

This vulnerability provider obtains information about security vulnerabilities from the Black Duck instance specified in the configuration.
The configuration is mandatory, because authentication is required.

:::note
The implementation is in *experimental* state.
:::

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

To enable this provider, pass `-a BlackDuck` on the command line.

## OSS Index

This vulnerability provider does not require any further configuration as it uses the public service at https://ossindex.sonatype.org/.
Before using this provider, please ensure to comply with its [Terms of Service](https://ossindex.sonatype.org/tos).

To enable this provider, pass `-a OssIndex` on the command line.

## OSV

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

To enable this provider, pass `-a OSV` on the command line.

## VulnerableCode

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

To enable this provider, pass `-a VulnerableCode` on the command line.
