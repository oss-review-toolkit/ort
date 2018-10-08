#!/bin/sh

# Check if all Kotlin files contain a copyright statement.

if git grep -L "Copyright" -- "*.kt"; then
  echo "Please add copyright statements to the above Kotlin files."
  exit 1
else
  echo "All Kotlin files have a copyright statement."
fi
