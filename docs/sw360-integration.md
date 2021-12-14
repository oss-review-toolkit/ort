# Integrating with SW360

OSS Review Toolkit (ORT) offers several ways to integrate with the software component catalog application [Eclipse SW360][sw360]:

- Upload ORT results to SW360
- Use SW360 package metadata curations as input for the ORT _analyzer_
- Store ORT _scanner_ results in SW360 for re-use in future scans

Each of these integration options is explained in detail below.

## Upload ORT Results to SW360

### When to use

To add packages found by ORT to projects and releases in SW360.

### Prerequisites

In order to be able to upload ORT results to SW360, first set the connection parameters to your SW360 instance.
You can do this by defining a `sw360Configuration` scanner storage in the `storages` section of your `ort.conf` file (e.g. in `${HOME}/.ort/conf`) or pass it to the ORT command with the `--config` option as shown below.

```
ort {
  scanner {
    storages {
      sw360Configuration {
        restUrl = "https://your-sw360-rest-url"
        authUrl = "https://your-authentication-url"
        username = username
        password = password
        clientId = clientId
        clientPassword = clientPassword
        token = token
      }
    }
  }
}
```

For a complete example of the `ort.conf` file see [reference.conf](../model/src/main/resources/reference.conf).

### Command Line

Uploading to SW360 is a stand-alone [ORT command](../cli/src/main/kotlin/commands/UploadResultToSw360Command.kt), which:

1. Takes an _analyzer_ result file as an input,
2. Creates components/releases in SW360 for the packages and ...
3. Creates the projects and links the created releases to the respective project.

```bash
cli/build/install/ort/bin/ort upload-result-to-sw360
  -i [analyzer-output-dir]/analyzer-result.yml
```

## Use SW360 Package Metadata Curations in ORT

### When to use

If you prefer to use the SW360 web frontend to correct package metadata instead of ORT's [curations.yml file](config-file-curations-yml.md).

Note:

1. Currently, only the SW360 fields `concludedLicenses`, `homepageUrl`, `binaryArtifact` and `sourceArtifact` are used for curations, all
   other SW360 fields are ignored as there are no corresponding fields for them in ORT.
2. A release in SW360 needs to be in the approved clearing state, otherwise the curated data will not be used.

### Prerequisites

In order to be able to use SW360 data in the ORT _analyzer_, first set the connection parameters for your SW360 instance.
You can do this by defining a `sw360Configuration` within the `analyzer` section of your `ort.conf` file (e.g. in
`${HOME}/.ort/conf`) or pass it to the ORT command with the `--config` option as shown below.

```
ort {
  analyzer {
    sw360Configuration {
      restUrl = "https://your-sw360-rest-url"
      authUrl = "https://your-authentication-url"
      username = username
      password = password
      clientId = clientId
      clientPassword = clientPassword
      token = token
    }
  }
}
```

For a complete example of the `ort.conf` file see [reference.conf](../model/src/main/resources/reference.conf).

### Command Line

To use SW360 curation, pass it to the `--sw360-curations` option of the _analyzer_:

```bash
cli/build/install/ort/bin/ort analyze
  -i [source-code-of-project-dir]
  -o [analyzer-output-dir]
  --sw360-curations
```

## Store ORT Scanner Results in SW360

### When to use

If you prefer to use the SW360 to store the ORT _scanner_ results instead of the other [storage backends][ort-storage-backends].

### Prerequisites

In order to be able to store ORT _scanner_ results SW360, first set the connection parameters to your SW360 instance.
You can do this by defining a `sw360Configuration` scanner storage in the `storages` section of your `ort.conf` file
(e.g. in `${HOME}/.ort/conf`) or pass it to the ORT command with the `--config` option as shown below.

```
ort {
  scanner {
    storages {
      sw360Configuration {
        restUrl = "https://your-sw360-rest-url"
        authUrl = "https://your-authentication-url"
        username = username
        password = password
        clientId = clientId
        clientPassword = clientPassword
        token = token
      }
    }
  }
}
```

The scan results for each package will be uploaded to SW360 once you have completed the above configuration. The
uploaded results will be used to speed up future scans.

Note the [SW360 attachment type][sw360-attachment-type] of the uploaded scan results is `SCAN_RESULT_REPORT`.

### Command Line

Apart from configuring your `ort.conf` to use SW360 to store scanner results, no specific option needs to be passed the _scanner_:

```bash
cli/build/install/ort/bin/ort scan 
  -i [source-code-of-project-dir]
  -o [analyzer-output-dir]
```

[ort-storage-backends]: https://github.com/oss-review-toolkit/ort#storage-backends
[sw360]: https://github.com/eclipse/sw360
[sw360-attachment-type]: https://github.com/eclipse/sw360/blob/master/clients/client/src/main/java/org/eclipse/sw360/clients/rest/resource/attachments/SW360AttachmentType.java
