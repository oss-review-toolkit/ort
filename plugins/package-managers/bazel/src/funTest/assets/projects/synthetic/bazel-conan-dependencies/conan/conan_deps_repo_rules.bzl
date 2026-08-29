# This bazel repository rule is used to load Conan dependencies into the Bazel workspace.
# It's used by a generated module file that provides information about the conan packages.
# Each conan package is loaded into a bazel repository rule, with having the name of the
# package. The whole method is based on symlinks to not copy the whole package into the
# Bazel workspace, which is expensive.
def _conan_dependency_repo(rctx):
    package_path = rctx.workspace_root.get_child(rctx.attr.package_path)

    child_packages = package_path.readdir()
    for child in child_packages:
        rctx.symlink(child, child.basename)

    rctx.symlink(rctx.attr.build_file_path, "BUILD.bazel")

conan_dependency_repo = repository_rule(
    implementation = _conan_dependency_repo,
    attrs = {
        "package_path": attr.string(
            mandatory = True,
            doc = "The path to the Conan package in conan cache.",
        ),
        "build_file_path": attr.string(
            mandatory = True,
            doc = "The path to the BUILD file.",
        ),
    },
)
