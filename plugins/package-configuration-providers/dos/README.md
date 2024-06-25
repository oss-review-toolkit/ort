# Double Open Server (DOS) Package Configuration Provider

The DOS package configuration provider provides license finding curations and path excludes for files of packages identified by purls.
As the provider works on a per-file basis, it should be used with either ORT's default ScanCode scanner wrapper with the `preferFileLicense` option enabled, or with the [DOS scanner wrapper].

## Usage

The DOS package configuration provider requires [DOS] to be running on a host and corresponding `DosPackageConfigurationProviderConfig` to be present in ORT's `config.yml`, e.g.

```yaml
ort:
  packageConfigurationProviders:
  - type: DOS
    options:
      url: '${DOS_SERVER_URL}'
    secrets:
      token: '${DOS_SERVER_TOKEN}'
```

where `DOS_SERVER_URL` and `DOS_SERVER_TOKEN` are environment variables that are set to the respective values.

[DOS]: https://github.com/doubleopen-project/dos
[DOS scanner wrapper]: ../../scanners/dos
