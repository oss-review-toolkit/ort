# How to make a license choice

When a dependency is multi-licensed (e.g., `MIT OR Apache-2.0`), use [license choices][ort-yml-license-choices] to select which license applies to your project.

## Choosing a license for a specific package

To select a license for a specific multi-licensed dependency, add a package license choice to your `.ort.yml` file:

```yaml
license_choices:
  package_license_choices:
  - package_id: "Maven:com.example:library:1.0.0"
    license_choices:
    - given: "MIT OR Apache-2.0"
      choice: "Apache-2.0"
```

## Choosing a license globally

To apply the same license choice to all packages that offer it, use a repository license choice:

```yaml
license_choices:
  repository_license_choices:
  - given: "MIT OR Apache-2.0"
    choice: "Apache-2.0"
```

Package-level choices override repository-level choices.

## Related resources

* Reference
  * [Repository configuration (.ort.yml)][ort-yml]

[ort-yml]: ../reference/configuration/ort-yml.md
[ort-yml-license-choices]: ../reference/configuration/ort-yml.md#license-choices
