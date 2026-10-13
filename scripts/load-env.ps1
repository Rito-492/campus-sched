# Load whitelisted keys from .env into the current process environment.
# Usage (dot-source): . ./scripts/load-env.ps1
# Only parses KEY=VALUE lines; never executes file content.

$whitelist = @(
    'DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USERNAME', 'DB_PASSWORD',
    'TEST_DATABASE_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD',
    'DEMO_DATE', 'DEMO_PASSWORD', 'APP_DEMO_SEED', 'APP_TEST_NOW',
    'BASE_URL'
)

$envFile = Join-Path $PSScriptRoot '..\.env'
if (-not (Test-Path $envFile)) {
    throw "Missing .env file: $envFile (copy from .env.example first)"
}

foreach ($line in Get-Content $envFile -Encoding UTF8) {
    $line = $line.Trim()
    # Skip blank lines and comments
    if ($line -eq '' -or $line.StartsWith('#')) { continue }

    # Parse KEY=VALUE only; do not evaluate the value
    $idx = $line.IndexOf('=')
    if ($idx -le 0) { continue }
    $key   = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()

    # Strip one pair of optional surrounding double quotes
    if ($value.Length -ge 2 -and $value[0] -eq '"' -and $value[$value.Length - 1] -eq '"') {
        $value = $value.Substring(1, $value.Length - 2)
    }

    if ($whitelist -contains $key) {
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}
