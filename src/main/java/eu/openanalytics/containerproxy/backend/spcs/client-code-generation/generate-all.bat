@echo off
setlocal enabledelayedexpansion

echo Generating code for all OpenAPI specifications...
echo.

for %%f in (specifications\*.yaml) do (
    echo Processing: %%f
    openapi-generator-cli generate -i "%%f" -g java -c generator-config.json
    if errorlevel 1 (
        echo ERROR: Failed to generate code for %%f
        pause
        exit /b 1
    )
    echo.
)

echo All specifications processed successfully!
pause

