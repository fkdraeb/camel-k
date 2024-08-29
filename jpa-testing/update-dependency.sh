#!/bin/bash

# Check if the correct number of arguments is provided
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <new-string>"
    exit 1
fi

# New string to replace with
NEW_STRING=$1

# Find all .yaml files and process them
find . -name "*.yaml" | while read -r FILE; do
    # Use sed to search for the string and replace it with the new string
                sed -i "s/\(org\.spms:jpa:0\.0\.3-\)[^\"]*/\1$NEW_STRING/g" "$FILE"
done

echo "Update complete."

