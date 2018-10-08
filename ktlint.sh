#!/bin/sh

# Run ktlint but exclude rules that are currently not applied to this project.

if ./ktlint | grep -Ev "(Wildcard import|Unexpected spacing before)"; then
  echo "Please correct above lint issues."
  exit 1
else
  echo "No relevant lint issues found."
fi
