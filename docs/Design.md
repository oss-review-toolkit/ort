# Package Manager Considerations

## Existing Definition File Parsers

### Python

- https://github.com/librariesio/pydeps/

  Uses `pip download`, but only supports `requirements.txt` files.

- https://github.com/sourcegraph/pydep

  Uses `pip.req.parse_requirements` for `requirements.txt` files (see [this article](http://jelly.codes/articles/python-pip-module/)) and a `distutils.core.setup` replacement to parse `setup.py` files (similar to [this approach](https://stackoverflow.com/a/27790447/1127485)).

- https://github.com/fossas/srclib-pip/

  An add-on for [srclib](https://github.com/sourcegraph/srclib) that manually parses `requirements.txt` files.
