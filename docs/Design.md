# Package Manager Considerations

## Existing Definition File Parsers

### Python

- https://github.com/pivotal/LicenseFinder/blob/master/lib/license_finder/package_managers/pip.rb

  Uses a [helper script](https://github.com/pivotal/LicenseFinder/blob/master/bin/license_finder_pip.py) to parse `requirements.txt` via the `pip.req.parse_requirements` API from Ruby code.

- https://github.com/librariesio/bibliothecary/blob/master/lib/bibliothecary/parsers/pypi.rb

  Manually parses `requirements.txt`, `setup.py` and `Pipfile{.lock}` files. Only supports basic syntax.

- https://github.com/librariesio/pydeps/

  Uses `pip download`, but only supports `requirements.txt` files.

- https://github.com/sourcegraph/pydep

  Uses `pip.req.parse_requirements` for `requirements.txt` files (see [this article](http://jelly.codes/articles/python-pip-module/)) and a `distutils.core.setup` replacement to parse `setup.py` files (similar to [this approach](https://stackoverflow.com/a/27790447/1127485)).

- https://github.com/fossas/srclib-pip/

  An add-on for [srclib](https://github.com/sourcegraph/srclib) that manually parses `requirements.txt` files.
