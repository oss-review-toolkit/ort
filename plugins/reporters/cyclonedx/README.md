# CycloneDX Reporter

## Property Taxonomy

This table defines the [taxonomy](https://cyclonedx.github.io/cyclonedx-property-taxonomy/) of ORT-specific property names:

| Property Name | Parent Entity | Description |
|---------------|---------------|-------------|
| `ort:dependencyType` | `component` | The type of dependency in relation to the parent component. Valid values: "direct", "transitive". |
| `ort:origin` | `license` | The origin of the license. Valid values: "declared license", "detected license", "concluded license". |
