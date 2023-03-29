import os
import shutil

from conans import CMake
from conans import ConanFile, tools


class PocoConan(ConanFile):
    name = "Poco"
    version = "1.9.3"
    url = "http://github.com/pocoproject/conan-poco"
    exports_sources = "CMakeLists.txt", "PocoMacros.cmake"  # REMOVE POCOMACROS IN NEXT VERSION!
    generators = "cmake", "txt"
    settings = "os", "arch", "compiler", "build_type"
    author = "The Author <author@example.org>"
    license = "The Boost Software License 1.0"
    description = "Modern, powerful open source C++ class libraries for building network- and internet-based " \
                  "applications that run on desktop, server, mobile and embedded systems."
    options = {"shared": [True, False],
               "fPIC": [True, False],
               "enable_xml": [True, False],
               "enable_json": [True, False],
               "enable_mongodb": [True, False],
               "enable_pdf": [True, False],
               "enable_util": [True, False],
               "enable_net": [True, False],
               "enable_netssl": [True, False],
               "enable_netssl_win": [True, False],
               "enable_crypto": [True, False],
               "enable_data": [True, False],
               "enable_data_sqlite": [True, False],
               "enable_data_mysql": [True, False],
               "enable_data_odbc": [True, False],
               "enable_sevenzip": [True, False],
               "enable_zip": [True, False],
               "enable_apacheconnector": [True, False],
               "enable_cppparser": [True, False],
               "enable_pocodoc": [True, False],
               "enable_pagecompiler": [True, False],
               "enable_pagecompiler_file2page": [True, False],
               "enable_redis": [True, False],
               "force_openssl": [True, False],  # "Force usage of OpenSSL even under windows"
               "enable_tests": [True, False],
               "poco_unbundled": [True, False],
               "cxx_14": [True, False]
              }
    default_options = '''
shared=False
fPIC=True
enable_xml=True
enable_json=True
enable_mongodb=True
enable_pdf=False
enable_util=True
enable_net=True
enable_netssl=True
enable_netssl_win=True
enable_crypto=True
enable_data=True
enable_data_sqlite=True
enable_data_mysql=False
enable_data_odbc=False
enable_sevenzip=False
enable_zip=True
enable_apacheconnector=False
enable_cppparser=False
enable_pocodoc=False
enable_pagecompiler=False
enable_pagecompiler_file2page=False
enable_redis=True
force_openssl=True
enable_tests=False
poco_unbundled=False
cxx_14=False
'''

    def source(self):
        zip_name = "poco-%s-release.zip" % self.version
        tools.download("https://github.com/pocoproject/poco/archive/%s" % zip_name, zip_name)
        tools.unzip(zip_name)
        shutil.move("poco-poco-%s-release" % self.version, "poco")
        os.unlink(zip_name)
        shutil.move("poco/CMakeLists.txt", "poco/CMakeListsOriginal.cmake")
        shutil.move("CMakeLists.txt", "poco/CMakeLists.txt")
        # Patch the PocoMacros.cmake to fix the detection of the win10 sdk.
        # NOTE: ALREADY FIXED IN POCO REPO, REMOVE THIS FOR NEXT VERSION
        shutil.move("PocoMacros.cmake", "poco/cmake/PocoMacros.cmake")

    def config_options(self):
        if self.settings.os == "Windows":
            del self.options.fPIC

    def configure(self):
        if self.options.enable_apacheconnector:
            raise Exception("Apache connector not supported: https://github.com/pocoproject/poco/issues/1764")

    def requirements(self):
        if self.options.enable_netssl or self.options.enable_netssl_win or self.options.enable_crypto or self.options.force_openssl:
            self.requires.add("openssl/3.0.0", private=False)

        if self.options.enable_data_mysql:
            # self.requires.add("MySQLClient/6.1.6@hklabbers/stable")
            raise Exception("MySQL not supported yet, open an issue here please: %s" % self.url)

    def build(self):
        if self.settings.compiler == "Visual Studio" and self.options.shared:
            self.output.warn("Adding ws2_32 dependency...")
            replace = 'Net Util Foundation Crypt32.lib'
            tools.replace_in_file("poco/NetSSL_Win/CMakeLists.txt", replace, replace + " ws2_32 ")

            replace = 'Foundation ${OPENSSL_LIBRARIES}'
            tools.replace_in_file("poco/Crypto/CMakeLists.txt", replace, replace + " ws2_32 Crypt32.lib")

        cmake = CMake(self, parallel=None)  # Parallel crashes building
        for option_name in self.options.values.fields:
            activated = getattr(self.options, option_name)
            if option_name == "shared":
                cmake.definitions["POCO_STATIC"] = "OFF" if activated else "ON"
            elif not option_name == "fPIC":
                cmake.definitions[option_name.upper()] = "ON" if activated else "OFF"

        if self.settings.os == "Windows" and self.settings.compiler == "Visual Studio":  # MT or MTd
            cmake.definitions["POCO_MT"] = "ON" if "MT" in str(self.settings.compiler.runtime) else "OFF"
        self.output.info(cmake.definitions)
        os.mkdir("build")
        cmake.configure(source_dir="../poco", build_dir="build")
        cmake.build()

    def package(self):
        # Copy the license files
        self.copy("poco/LICENSE", dst=".", keep_path=False)
        # Typically includes we want to keep_path=True (default)
        packages = ["CppUnit", "Crypto", "Data", "Data/MySQL", "Data/ODBC", "Data/SQLite",
                    "Foundation", "JSON", "MongoDB", "Net", "Redis", "Util",
                    "XML", "Zip"]
        if self.settings.os == "Windows" and self.options.enable_netssl_win:
            packages.append("NetSSL_Win")
        else:
            packages.append("NetSSL_OpenSSL")

        for header in packages:
            self.copy(pattern="*.h", dst="include", src="poco/%s/include" % header)

        # But for libs and dlls, we want to avoid intermediate folders
        self.copy(pattern="*.lib", dst="lib", src="build/lib", keep_path=False)
        self.copy(pattern="*.a",   dst="lib", src="build/lib", keep_path=False)
        self.copy(pattern="*.dll", dst="bin", src="build/bin", keep_path=False)
        # in linux shared libs are in lib, not bin
        self.copy(pattern="*.so*", dst="lib", src="build/lib", keep_path=False, symlinks=True)
        self.copy(pattern="*.dylib", dst="lib", src="build/lib", keep_path=False)

    def package_info(self):
        """ Define the required info that the consumers/users of this package will have
        to add to their projects
        """
        libs = [("enable_mongodb", "PocoMongoDB"),
                ("enable_pdf", "PocoPDF"),
                ("enable_netssl", "PocoNetSSL"),
                ("enable_netssl_win", "PocoNetSSLWin"),
                ("enable_net", "PocoNet"),
                ("enable_crypto", "PocoCrypto"),
                ("enable_data_sqlite", "PocoDataSQLite"),
                ("enable_data_mysql", "PocoDataMySQL"),
                ("enable_data_odbc", "PocoDataODBC"),
                ("enable_data", "PocoData"),
                ("enable_sevenzip", "PocoSevenZip"),
                ("enable_zip", "PocoZip"),
                ("enable_apacheconnector", "PocoApacheConnector"),
                ("enable_util", "PocoUtil"),
                ("enable_xml", "PocoXML"),
                ("enable_json", "PocoJSON"),
                ("enable_redis", "PocoRedis")]

        suffix = str(self.settings.compiler.runtime).lower()  \
                 if self.settings.compiler == "Visual Studio" and not self.options.shared \
                 else ("d" if self.settings.build_type=="Debug" else "")

        for flag, lib in libs:
            if getattr(self.options, flag):
                if self.settings.os == "Windows" and flag == "enable_netssl" and self.options.enable_netssl_win:
                    continue

                if self.settings.os != "Windows" and flag == "enable_netssl_win":
                    continue

                self.cpp_info.libs.append("%s%s" % (lib, suffix))

        self.cpp_info.libs.append("PocoFoundation%s" % suffix)

        # in linux we need to link also with these libs
        if self.settings.os == "Linux":
            self.cpp_info.libs.extend(["pthread", "dl", "rt"])

        if not self.options.shared:
            self.cpp_info.defines.extend(["POCO_STATIC=ON", "POCO_NO_AUTOMATIC_LIBS"])
            if self.settings.compiler == "Visual Studio":
                self.cpp_info.libs.extend(["ws2_32", "Iphlpapi", "Crypt32"])
