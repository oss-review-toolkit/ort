---
sidebar_position: 2
---

# Advisor

The *advisor* retrieves security advisories from configured services. It requires the analyzer result as an input. For
all the packages identified by the analyzer, it queries the services configured for known security vulnerabilities. The
vulnerabilities returned by these services are then stored in the output result file together with additional
information like the source of the data and a severity (if available).

Multiple providers for security advisories are available. The providers require specific configuration in the
[ORT configuration file](./model/src/main/resources/reference.yml), which needs to be placed in the *advisor*
section. When executing the advisor the providers to enable are selected with the `--advisors` option (or its short
alias `-a`); here a comma-separated list with provider IDs is expected. The following sections describe the providers
supported by the advisor:

## NexusIQ

A security data provider that queries [Nexus IQ Server](https://help.sonatype.com/iqserver). In the configuration,
the URL where Nexus IQ Server is running and the credentials to authenticate need to be provided:

```yaml
ort:
  advisor:
    nexusIq:
      serverUrl: "https://nexusiq.ossreviewtoolkit.org"
      username: myUser
      password: myPassword
```

To enable this provider, pass `-a NexusIQ` on the command line.

## OSS Index

This vulnerability provider does not require any further configuration as it uses the public service at
https://ossindex.sonatype.org/. Before using this provider, please ensure to comply with its
[Terms of Service](https://ossindex.sonatype.org/tos).

To enable this provider, pass `-a OssIndex` on the command line.

## VulnerableCode

This provider obtains information about security vulnerabilities from a
[VulnerableCode](https://github.com/nexB/vulnerablecode) instance. The configuration is limited to the server URL, as
authentication is not required:

```yaml
ort:
  advisor:
    vulnerableCode:
      serverUrl: "http://localhost:8000"
```

To enable this provider, pass `-a VulnerableCode` on the command line.

## OSV

This provider obtains information about security vulnerabilities from Google [OSV](https://osv.dev/), a distributed
vulnerability database for Open Source. The database aggregates data from different sources for various ecosystems. The
configuration is optional and limited to overriding the server URL.

```yaml
ort:
  advisor:
    osv:
      serverUrl: "https://api-staging.osv.dev"
```

To enable this provider, pass `-a OSV` on the command line.
