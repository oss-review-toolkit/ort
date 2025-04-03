# This Bazel module should be loaded by your WORKSPACE file.
# Add these lines to your WORKSPACE one (assuming that you're using the "bazel_layout"):
# load("@//conan:dependencies.bzl", "load_conan_dependencies")
# load_conan_dependencies()

def load_conan_dependencies():
    native.new_local_repository(
        name="fmt",
        path="~/.conan2/p/fmt1eb60e0d8556e/p",
        build_file="conan/fmt/BUILD.bazel",
    )
    native.new_local_repository(
        name="libcurl",
        path="~/.conan2/p/b/libcu1431bc73699fd/p",
        build_file="conan/libcurl/BUILD.bazel",
    )
    native.new_local_repository(
        name="openssl",
        path="~/.conan2/p/opens0aa3b75222f02/p",
        build_file="conan/openssl/BUILD.bazel",
    )
    native.new_local_repository(
        name="zlib",
        path="~/.conan2/p/zlib9780dc2008618/p",
        build_file="conan/zlib/BUILD.bazel",
    )
    native.new_local_repository(
        name="gtest",
        path="~/.conan2/p/b/gtestdb898f69ecf80/p",
        build_file="conan/gtest/BUILD.bazel",
    )
