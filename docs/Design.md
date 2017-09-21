# Package Manager Considerations

## Python

### Existing Definition File Parsers

- https://github.com/kennethreitz/pipenv

  A new package manager designed around `Pipfile{.lock}` files. Claims to offer [import of requirements.txt files](http://docs.pipenv.org/en/latest/advanced.html#importing-from-requirements-txt) and [import of setup.py files](https://github.com/kennethreitz/pipenv/issues/592), but it does not actually work, or only in very simple cases.

- https://github.com/pivotal/LicenseFinder/blob/master/lib/license_finder/package_managers/pip.rb

  Uses a [helper script](https://github.com/pivotal/LicenseFinder/blob/master/bin/license_finder_pip.py) to parse only `requirements.txt` via the `pip.req.parse_requirements` API from Ruby code.

- https://github.com/librariesio/bibliothecary/blob/master/lib/bibliothecary/parsers/pypi.rb

  Manually parses `requirements.txt`, `setup.py` and `Pipfile{.lock}` files. Only supports basic syntax.

- https://github.com/librariesio/pydeps/

  Uses `pip download`, but only supports `requirements.txt` files.

- https://github.com/sourcegraph/pydep

  Uses `pip.req.parse_requirements` for `requirements.txt` files (see [this article](http://jelly.codes/articles/python-pip-module/)) and a `distutils.core.setup` replacement to parse `setup.py` files (similar to [this approach](https://stackoverflow.com/a/27790447/1127485)). Has JSON output, but outputs dependencies in a flat list as opposed to a tree.

- https://github.com/fossas/srclib-pip/

  An add-on for [srclib](https://github.com/sourcegraph/srclib) that manually parses `requirements.txt` files only.

- https://github.com/blackducksoftware/hub-detect/blob/master/src/main/resources/pip-inspector.py

  Uses the `pip.req.parse_requirements` API. `setup.py` seems to be recognized but not yet handled.

### Conclusions

While using `pip.req.parse_requirements` to parse `requirements.txt` files and using `distutils.core.setup` to parse `setup.py` files basically is an elegant solution, that approach cannot handle e.g. custom / extra index URLs defined as part of `requirements.txt` files like:

    # common requirements
    -i http://foo.bar.com/ptpi/devpi/dev/+simple/

That is why calling `pip` and inspecting the output probably is the way to go to correctly determine the dependencies, even if that already downloads the dependencies at a stage where we would only need the dependency graph.
