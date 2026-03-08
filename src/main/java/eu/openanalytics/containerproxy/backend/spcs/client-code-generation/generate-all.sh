#!/bin/bash

echo "Generating code for all OpenAPI specifications..."
echo ""

# Find all YAML files in specifications folder
for spec_file in specifications/*.yaml; do
    if [ -f "$spec_file" ]; then
        echo "Processing: $(basename "$spec_file")"
        openapi-generator-cli generate -i "$spec_file" -g java -c generator-config.json
        
        if [ $? -ne 0 ]; then
            echo "ERROR: Failed to generate code for $spec_file"
            exit 1
        fi
        echo ""
    fi
done

echo "All specifications processed successfully!"

