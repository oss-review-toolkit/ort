# SW360 Integration

OSS Review Toolkit (ORT) offers several ways to integrate with the software component catalog application [Eclipse SW360][sw360]:

* Upload ORT *analyzer* results to SW360
* Use SW360 package metadata curations as input for the ORT *analyzer*
* Store ORT *scanner* results in SW360 for re-use in future scans

Each of these integration options is explained in detail below.

## Upload ORT Results to SW360

### When to use

To add packages found by ORT to projects and releases in SW360.

### Prerequisites

To be able to upload ORT results to SW360, first set the connection parameters to your SW360 instance.
You can do this by defining a `sw360Configuration` scanner storage in the `storages` section of your [config.yml](../getting-started/usage.md#ort-configuration-file) (e.g. in `$HOME/.ort/config`) or pass it to the ORT command with the `--config` option as shown below.

```yaml
ort:
  scanner:
    storages:
      sw360Configuration:
        restUrl: "https://your-sw360-rest-url"
        authUrl: "https://your-authentication-url"
        username: username
        password: password
        clientId: clientId
        clientPassword: clientPassword
        token: token
```

### Command Line

Uploading to SW360 is a stand-alone [ORT command](https://github.com/oss-review-toolkit/ort/blob/main/plugins/commands/upload-result-to-sw360/src/main/kotlin/UploadResultToSw360Command.kt), which:

1. Takes an *analyzer* result file as an input,
2. Creates components/releases in SW360 for the packages and ...
3. Creates the projects and links the created releases to the respective project.

```shell
cli/build/install/ort/bin/ort upload-result-to-sw360
  -i [analyzer-output-dir]/analyzer-result.yml
```

## Use SW360 Package Metadata Curations in ORT

### When to use

If you prefer to use the SW360 web frontend to correct package metadata instead of ORT's [curations.yml file](../configuration/package-curations.md).

Note:

1. Currently, only the SW360 fields `concludedLicenses`, `homepageUrl`, `binaryArtifact` and `sourceArtifact` are used for curations, all other SW360 fields are ignored as there are no corresponding fields for them in ORT.
2. A release in SW360 needs to be in the approved clearing state, otherwise the curated data will not be used.

### Prerequisites

To be able to use SW360 data in the ORT *analyzer*, first set the connection parameters for your SW360 instance.
You can do this by adding an entry of type `SW360` to the list of `packageCurationProviders` in your [config.yml](../getting-started/usage.md#ort-configuration-file) (e.g. in `$HOME/.ort/config`) or pass it to the ORT command with the `--config` option as shown below.

```yaml
ort:
  packageCurationProviders:
  - type: SW360
    config:
      restUrl: "https://your-sw360-rest-url"
      authUrl: "https://your-authentication-url"
      username: username
      password: password
      clientId: clientId
      clientPassword: clientPassword
      token: token
```

### Command Line

Apart from configuring your `config.yml` to use the SW360 curation provider, no specific option has to be passed to the *analyzer*:

```shell
cli/build/install/ort/bin/ort analyze
  -i [source-code-of-project-dir]
  -o [analyzer-output-dir]
```

## Store ORT Scanner Results in SW360

### When to use

If you prefer to use the SW360 to store the ORT *scanner* results instead of the other [storage backends][ort-storage-backends].

### Prerequisites

To be able to store ORT *scanner* results SW360, first set the connection parameters to your SW360 instance.
You can do this by defining a `sw360Configuration` scanner storage in the `storages` section of your `config.yml` file (e.g. in `$HOME/.ort/config`) or pass it to the ORT command with the `--config` option as shown below.

```yaml
ort:
  scanner:
    storages:
      sw360Configuration:
        restUrl: "https://your-sw360-rest-url"
        authUrl: "https://your-authentication-url"
        username: username
        password: password
        clientId: clientId
        clientPassword: clientPassword
        token: token
```

The scan results for each package will be uploaded to SW360 once you have completed the above configuration.
The uploaded results will be used to speed up future scans.

Note the [SW360 attachment type][sw360-attachment-type] of the uploaded scan results is `SCAN_RESULT_REPORT`.

### Command Line

Apart from configuring your `config.yml` to use SW360 to store scan results, no specific option needs to be passed to the *scanner*:

```shell
cli/build/install/ort/bin/ort scan 
  -i [source-code-of-project-dir]
  -o [analyzer-output-dir]
```

[ort-storage-backends]: https://github.com/oss-review-toolkit/ort#storage-backends
[sw360]: https://github.com/eclipse/sw360
[sw360-attachment-type]: https://github.com/eclipse/sw360/blob/master/clients/client/src/main/java/org/eclipse/sw360/clients/rest/resource/attachments/SW360AttachmentType.java
