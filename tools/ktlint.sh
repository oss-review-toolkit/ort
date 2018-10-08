#!/bin/sh

# Run ktlint but exclude rules that are currently not applied to this project.

BASEDIR=$(dirname "$0")

if "$BASEDIR/ktlint" | grep -Ev "(Wildcard import|Unexpected spacing before)"; then
  echo "Please correct above ktlint issues."
  exit 1
else
  echo "No relevant ktlint issues found."
fi
