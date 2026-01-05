# How to include dirs or files

Use includes to explicitly mark which parts of your project are distributed to third parties. Everything not included is treated as excluded.

## Including paths

Add [path includes][ort-yml-path-includes] to your `.ort.yml` file:

```yaml
includes:
  paths:
    - pattern: "src/**"
      reason: "SOURCE_OF"
      comment: "Source code distributed in release artifacts"
    - pattern: "lib/**"
      reason: "SOURCE_OF"
      comment: "Library code distributed in release artifacts"
```

Unlike excludes, includes only support paths (not scopes).

## Related resources

* Reference
  * [Repository configuration (.ort.yml)][ort-yml]

[ort-yml]: ../reference/configuration/ort-yml.md
[ort-yml-path-includes]: ../reference/configuration/ort-yml.md#path-includes
