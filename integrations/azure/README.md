# Azure ORT Starter Pipeline

The starter pipeline allows you to easily run an ORT scan on [Azure DevOps](https://azure.microsoft.com/services/devops/).

To run the pipeline, simply:

- Create a new YAML-based pipeline in your Azure DevOps project based on [ort-pipeline.yml](./ort-pipeline.yml) in this repository
- Run the pipeline with the preferred set of parameters

## Directory description

This subdirectory has the following structure:
```bash
azure
├── credentials
│   ├── write-config-netrc.yml
│   └── write-downloader-netrc.yml
├── docker
│   ├── docker-build-image.yml
│   └── docker-ort-run.yml
├── ort-pipeline.yml
├── README.md
└── steps
    └── complete-ort-run.yml
```
The folder `credentials` contains templates to write `.netrc` files (see the [specification](https://www.gnu.org/software/inetutils/manual/html_node/The-_002enetrc-file.html):
- `write-config-netrc.yml` converts the parameter group  `ort_config_credentials` to `.netrc` used when downloading the configuration
- `write-downloader-netrc.yml` converts the parameter group `ort_repo_credentials` to `.netrc` used to download the project
Exchange those templates with custom templates for different credential handling

The folder `docker` contains files to run ORT in a docker container:
- `docker-build-image.yml` is a template to build the ORT image based on the [Dockerfile](../../Dockerfile)
- `docker-ort-run.yml` contains steps to download a configuration repository, download the project and then analyze, scan and evaluate the project, finally creating reports

`ort-pipeline-yml` contains the starter pipeline, consisting of one stage running on a default Ubuntu agent, as well as providing parameters for custom runs.

## Setting up credentials

The pipeline supports using simple credentials (username, password) to download a repository.
Credentials can be set up in the following way:

- Set the parameter `Use repository credentials as defined in variable group ort_repo_credentials`
- Add a variable group called `ort_repo_credentials` (via "Library" -> "Variable groups")
- Add the variables `USERNAME` and `PASSWORD` to this group (for security, make password a secret)
- Note: The names have to match exactly

This will result in the build using these credentials to download your project.

## Using a different repository for ORT configuration

The pipeline supports using a different repository (with different credentials) to use for a pipeline run.
Simply fill the two parameters `Config Version Control URL` and optionally change `Config Revision`
If you need authentication to access the repository, you can do so via login and password:

- Check the parameter `Use credentials for ort as defined in variable group ort_config_credentials`
- Add a variable group called `ort_config_credentials` (via "Library" -> "Variable groups")
- Add the variables `ORT_USERNAME` and `ORT_PASSWORD` to this group (for security, make password a secret)
- Note: The names have to be exactly the ones described above: Azure devops doesn't currently allow dynamic variable groups

If you do not wish to use a config repository, leave `Config Version Control URL` set to `none`.

## Known Limitations

Note that you probably don't want to use this pipeline for your production setup due to the following limitations:

- The pipeline currently runs on hosted agents.
  This limits pipeline runs to 1 hour (private) and 6 hours (public) repository.
- In addition, public pipelines don't allow easy Docker image caching between runs, so the Docker image is built within the run.
- Scan results are stored locally on the agents.
  They will not be reused between runs.
- The pipeline writes credentials to the agent.
  This can be a major security concern if you do not control the agent and will be fixed in some future version.
- The version of ORT to use is not configurable.
  The pipeline will always use ORT master to run.
