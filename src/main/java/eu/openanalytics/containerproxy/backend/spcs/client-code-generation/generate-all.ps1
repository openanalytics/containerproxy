Write-Host "Generating code for all OpenAPI specifications..." -ForegroundColor Green
Write-Host ""

$specFiles = Get-ChildItem -Path "specifications" -Filter "*.yaml"

foreach ($file in $specFiles) {
    Write-Host "Processing: $($file.Name)" -ForegroundColor Cyan
    openapi-generator-cli generate -i $file.FullName -g java -c generator-config.json
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Failed to generate code for $($file.Name)" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
}

Write-Host "All specifications processed successfully!" -ForegroundColor Green

