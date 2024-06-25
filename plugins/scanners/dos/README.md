# Double Open Server (DOS) Scanner Wrapper

The DOS scanner wrapper is a client for [DOS] to initiate scans on the server-side, performed by [ScanCode], and to reuse scan results on a per-file basis.
This effectively implements "delta-scanning" of only the changed files between package versions.
Other configured scan storages are not used by this scanner implementation.

## Usage

The DOS scanner wrapper requires [DOS] to be running on a host and a corresponding `DosScannerConfig` to be present in ORT's `config.yml`:

```yaml
ort:
  scanner:
    config:
      DOS:
        options:
          url: '${DOS_SERVER_URL}'
          frontendUrl: '${DOS_FRONTEND_URL}'
        secrets:
          token: '${DOS_SERVER_TOKEN}'
```

`DOS_SERVER_URL`, `DOS_FRONTEND_URL` and `DOS_SERVER_TOKEN` are environment variables that are set to the respective values.
The `DOS_FRONTEND_URL` is meant to point to the Clearance UI which can be used to inspect scan results and to create license finding curations for use with the [DOS Package Configuration Provider].

[DOS]: https://github.com/doubleopen-project/dos
[ScanCode]: https://github.com/nexB/scancode-toolkit
[DOS Package Configuration Provider]: ../../package-configuration-providers/dos
