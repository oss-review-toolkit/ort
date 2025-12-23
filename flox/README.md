# Flox environments for ORT

## Introduction

[Flox] is a tool to define local development [environments] (and share them) based on [Nix packages].
Such environments can also be [turned into Docker images].

## Structure

ORT provides several [environments] that build upon each other:

* `flox/minimal/.flox/env/manifest.toml`: Contains the bare minimum tools to run Gradle.
* `flox/external-tools/.flox/env/manifest.toml`: A [composed environment] that bundles all required external tools from other [environments].

Then, plugin parent projects may contain further [composed environment]s that bundle external tools from the individual plugins, for example:

* `plugins/scanners/.flox/env/manifest.toml`: Bundles all [environments] for scanner tools.
* `plugins/scanners/scancode/.flox/env/manifest.toml`: The environment for the ScanCode tool.
* ...

Note that [environments] "stack up" and augment the tools already available on the host system.
This sometimes is undesirable, for example when trying to find out whether an environment in itself provides all required tools.

For this purpose, the `flox/run_isolated.sh` provides an easy way to run a command in an environment that *only* provides the tools from that environment and nothing else.

An example is to check whether the external-tools environment provides all requirements to run ORT:

```shell
flox/run_isolated.sh external-tools ./gradlew -q :cli:run --args="requirements"
```

## Maintenance

As manifest files are accompanied by lockfiles they are usually not edited directly, but via the `flox edit` command which automatically updates the lockfile.

### Adding a new environment

Run `flox init -d <environment directory>` where `<environment directory>` is the directory to create the new environment in.
For example, use `flox init -d plugins/version-control-systems/git` if you want to create a new environment for Git-related tools.

Then follow the steps in the next section to update the environment.

### Updating an existing environment

To update the version used for a tool or to add another tool in the same environment, run `flox edit -d <environment directory>`, make your changes, and exit the editor.
For example, use `flox edit -d plugins/version-control-systems/git` if you want to make changes to the environment of Git-related tools.

### Propagating changes

Also composed environments are accompanied by lockfiles.
As these lockfiles also lock the contents of all included environments, the lockfiles of composed environments need to be updated when an included environment changes.
This is not (yet) done automatically by [Flox], but needs to be done manually by running `flox include upgrade`.

[Flox]: https://flox.dev/
[environments]: https://flox.dev/docs/concepts/environments/
[Nix packages]: https://search.nixos.org/packages
[turned into Docker images]: https://flox.dev/docs/man/flox-containerize/
[composed environment]: https://flox.dev/docs/concepts/composition/
