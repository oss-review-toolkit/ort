#!/bin/bash

conan_option=${CONAN_SERIES:-2}
create_profile=${CONAN_CREATE_PROFILE:-true}

# Setup pyenv
eval "$(pyenv init - bash)"
eval "$(pyenv virtualenv-init -)"

# Setting up Conan 1.x
if [[ "$conan_option" -eq 1 ]]; then # Setting up Conan 1.x series
    pyenv activate conan
    if "$create_profile"; then
        echo "Creating Conan profile."
        conan profile new default --detect
        # Docker has modern libc
        conan profile update settings.compiler.libcxx=libstdc++11 default
    fi
    echo "Using Conan 1.x series."
elif [[ "$conan_option" -eq 2 ]]; then # Setting up Conan 2.x series
    pyenv activate conan2
    if "$create_profile"; then
        echo "Creating Conan profile."
        conan profile detect --force
    fi
    echo "Using Conan 2.x series."
fi  

# Runs conan from activated profile
conan "$@"

