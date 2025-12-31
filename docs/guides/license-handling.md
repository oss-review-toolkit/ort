# License Handling

## License types

ORT deals with various types of licenses.
Their ORT-specific names and purposes are explained below.

### Declared license

The ORT *analyzer* picks up declared licenses that are provided as part of package manager specific metadata, like inside the `<licenses>` tag in the `pom.xml` for [Maven][1] or inside the `license` field in the `package.json` for [NPM][2].
In other words, this is the license the author of the package claims or intends the package to be licensed under; the license that is "visible from the outside".
ORT does *not* consider licenses mentioned inside source code files as declared licenses (see [detected licenses](#detected-license) below).
As such, the declared license alone only provides an incomplete picture.
There are so-called "envelope cases" where the license visible from the outside (*on* the envelope) does not match what is *inside* the envelope (i.e. in the source code).
For example, a package might have declared itself to be licensed under the MIT license, but in the source code a file might contain a BSD-3-Clause license header.

Declared licenses often come in non-SPDX form or contain typos.
For universally valid cases, ORT has built-in [mappings](https://github.com/oss-review-toolkit/ort/blob/main/utils/spdx/src/main/resources/declared-license-mapping.yml).
For cases that might be ambiguous in general but are valid in the specific context of a package, [curations](../configuration/package-curations.md) can be used to define a `declared_license_mapping`.

### Detected license

Detected licenses are those licenses that are detected via an ORT *scanner* implementation by looking at the contents of all source code files belonging to a package, in particular at the contents of license files or copyright headers in source code files.
Detected licenses complement the picture created by declared license by revealing envelope cases where the declared and detected licenses do not match.

### Main License

The main license is a convenience construct that combines the declared license(s) with those detected license(s) whose findings stem from the `licenseFilePatterns` configured in `config.yml`.

### Concluded license

The concluded license is manually created via a [curation](../configuration/package-curations.md).
In cases where the union of declared and detected licenses is wrong (e.g. due to mistakes in metadata or false positives from scanners), the concluded license can be used to set which licenses actually match reality.
Curating a concluded license should be an objective decision based on verifiable facts.
It should not yet apply a license choice, as it is the complete license expression a package can theoretically be used under.

### Effective license

The effective license finally is the one that takes effect for the package, taking into account any project-specific context like making a [license choice](../configuration/ort-yml.md#license-choices) in case of dual-licensing.
This is the license that should primarily be used in ORT's *evaluator* rules.

## Curating licenses

Curating licenses via a [concluded license](#concluded-license) is somewhat of a "sledgehammer" method as it overrides any declared and detected licenses.
This can be a problem if a license curation should be reused also for future versions of a package:
There is a risk that a newer package version introduces new licenses, which would go unnoticed with a concluded license that blindly overrides everything.

That is why in such scenarios, a [license finding curation](../configuration/package-configurations.md#defining-path-excludes-and-license-finding-curations) as part of a package configuration is the better option, as it allows concluding a single exact finding of a license.
That way, unmatched licenses are not affected by the curation, and new / changed licenses will not go unnoticed.

[1]: https://maven.apache.org/pom.html#Licenses
[2]: https://docs.npmjs.com/cli/v8/configuring-npm/package-json#license
