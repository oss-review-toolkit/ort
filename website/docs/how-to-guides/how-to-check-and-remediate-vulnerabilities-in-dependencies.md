# How to check and remediate vulnerabilities in dependencies

Use the [Advisor] to check your dependencies for known security vulnerabilities.

## Running vulnerability checks

Run the Advisor with one or more vulnerability providers:

```shell
ort advise \
  -i analyzer-result.yml \
  -o <advisor-output-dir> \
  -a OSV,OSSIndex
```

## Resolving a vulnerability

When a vulnerability is reported but doesn't apply to your use case (e.g., only in test dependencies), add a [vulnerability resolution][resolutions-vulnerabilities] to your `.ort.yml` file:

```yaml
resolutions:
  vulnerabilities:
  - id: "CVE-2024-6763"
    reason: "INEFFECTIVE_VULNERABILITY"
    comment: "The vulnerable package is a transitive dependency of wiremock which is only used for testing."
```

## Related resources

* Reference
  * [Advisor CLI][advisor]
  * [Resolutions][resolutions]

[advisor]: ../reference/cli/advisor.md
[resolutions]: ../reference/configuration/resolutions.md
[resolutions-vulnerabilities]: ../reference/configuration/resolutions.md#file-format
