# Hints for using ORT with Docker

## General Hints

### Mount current working directory or directory relative to working directory

```shell
docker run \
  -v $PWD/:/project \ # Mount current working directory into /project to use as input.
  ort --info \
  -c /project/ort/config.hocon \ # Use file from "<workingdirectory>/ort" as config.
  analyze -i /project [...] # Insert further arguments for the command.
```

If only a subproject shall be analyzed, change the input path `-i /project` to `-i /project/subproject`. Note that still the projects root directory needs to be mounted to Docker for ORT to detect VCS information.

**Note:** The single forward slash `/` between the environment variable `$PWD` and the `:` is required for PowerShell compatibility, as PowerShell otherwise interprets `:` as part of the environment variable. 

### Setting custom certificates to Docker image keystore

It is possible to install custom certificates when building the image so that they are installed into various keystores.
To do this, specify `--build-arg CRT_FILES=<path>` when running `docker build`. 
Note that this requires the directory or certificate file to be inside the Docker build context (usually the directory where the build is run, i.e. probably the directory the Dockerfile resides in). 
Otherwise, the directories cannot be copied into the Docker image.

## Common Issues

### Docker build fails with an "SSL Handshake" error

Some web proxies, such as from Blue Coat (Symantec) [do not support TLSv1.3](https://en.wikipedia.org/wiki/Transport_Layer_Security#TLS_1.3), which leads to errors when Docker tries to establish a connection through them. The following steps allow to force a specific TLS version to be used: 

1. Insert `ENV JAVA_OPTS="-Djdk.tls.client.protocols=TLSv1.2"` in the Dockerfile, below the `FROM` line to force a specific TLS version.
2. Run the build again, it should succeed now.

### Authenticating with a private Git repository fails

To authenticate with a private Git repository, ORT uses the (semi)standardized `.netrc` file. With the following steps, `.netrc` can be used with Docker.

1. Create a `.netrc` file:
   
   ```
   machine <hostname> login <username> password <password>
   ```

   For example:

   ```
   machine github.com login mygituser password mypersonaltoken
   ```

   Ensure that there are no additional spaces, newlines, tabs, or other whitespace characters after the hostname, username, or password. Some third-party tools would otherwise interpret these whitespace characters as part of the authentication string and cause authentication errors with some providers.

   **Note:** If you receive any authentication errors, double-check this file. The format often shared online with the properties separated by newlines does **not** work with Docker images for ORT, as not all included third-party tools support this format.

2. Mount the `.netrc` into the home directory of the ORT Docker container. By default, that is the `/root` directory: 
   
   ```shell
   docker run -v <workspace_path>:/project -v <netrc_folder_path>/.netrc:/root/.netrc ort --info scan (...)
   ```

   **Important:** Ensure that the `.netrc` file has been created at `netrc_folder_path` before running the command, otherwise Docker will create a folder `.netrc` inside the container, instead of mounting a single file.

It is also possible to use `git config` to set up access to private repositories.

1. Configure your `.gitconfig` with your GitHub credentials

    ```shell
    [url "https://<username>:<password>@github.com"]
	         insteadOf = https://github.com    
    ```
2. Mount the `.gitconfig` into the home directory of the ORT Docker container. By default, that is the `/root` directory:

    ```shell
    docker run -v <workspace_path>:/project -v <gitconfig_path>:/root/.gitconfig ort --info scan (...)
    ```

   **Important:** Ensure that the `.gitconfig` file has been created at `gitconfig_path` before running the command, otherwise Docker will create a folder `.gitconfig` inside the container, instead of mounting a single file.
