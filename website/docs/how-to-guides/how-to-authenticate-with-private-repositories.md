# How to authenticate with private repositories

ORT needs credentials to access private Git repositories when downloading source code for scanning.

## Using .netrc

Create a `.netrc` file with your credentials:

```
machine <hostname> login <username> password <password>
```

For example, to authenticate with GitHub:

```
machine github.com login mygituser password mypersonaltoken
```

Ensure there are no additional spaces, newlines, tabs, or other whitespace characters after the hostname, username, or password. Some tools interpret whitespace as part of the authentication string, causing errors.

### Using .netrc with Docker

Mount the `.netrc` file into the ORT container's home directory (`/home/ort`):

```shell
docker run \
  -v /path/to/project:/project \
  -v /path/to/.netrc:/home/ort/.netrc \
  ghcr.io/oss-review-toolkit/ort analyze -i /project -o /project
```

Ensure the `.netrc` file exists before running the command. If it doesn't, Docker will create a directory named `.netrc` instead of mounting a file.

## Using .gitconfig

Alternatively, configure Git to rewrite URLs with embedded credentials:

```gitconfig
[url "https://<username>:<password>@github.com"]
    insteadOf = https://github.com
```

### Using .gitconfig with Docker

Mount the `.gitconfig` file into the ORT container's home directory:

```shell
docker run \
  -v /path/to/project:/project \
  -v /path/to/.gitconfig:/home/ort/.gitconfig \
  ghcr.io/oss-review-toolkit/ort analyze -i /project -o /project
```

Ensure the `.gitconfig` file exists before running the command.

## Related resources

* Getting Started
  * [Docker](../getting-started/docker.md)
* Reference
  * [Downloader CLI](../reference/cli/downloader.md)
  * [Scanner CLI](../reference/cli/scanner.md)
