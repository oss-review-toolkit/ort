#!/bin/bash

if [ ! -d "$1" ]; then
    echo "Please specify a directory."
    exit 1
fi

# Make the dependency plugin's goal reactor-aware by specifying "compile" as a dummy phase in the same run, see
# http://stackoverflow.com/a/1905927/1127485
mvn -B -f $1/pom.xml compile dependency:list 2>/dev/null | sed -nr "s/^\[INFO\]    (.+:.+:.+)$/\1/p" | sort -u
