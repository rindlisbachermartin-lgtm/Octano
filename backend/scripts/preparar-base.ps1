$ErrorActionPreference = 'Stop'

# Se ejecuta desde backend/. No imprime ni guarda contraseñas adicionales.
$configPath = Join-Path $PSScriptRoot '../.env.properties'
$localConfig = @{}
if (Test-Path -LiteralPath $configPath) {
    $localConfig = ConvertFrom-StringData (Get-Content -LiteralPath $configPath -Raw)
}
$dbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } elseif ($localConfig.DB_USERNAME) { $localConfig.DB_USERNAME } else { 'postgres' }
$dbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { $localConfig.DB_PASSWORD }
if (-not $dbPassword) { throw 'Completá DB_PASSWORD en backend/.env.properties o en las variables de entorno.' }

$psql = Join-Path $env:ProgramFiles 'PostgreSQL/18/bin/psql.exe'
if (-not (Test-Path -LiteralPath $psql)) { throw 'No se encontró psql de PostgreSQL 18. Ajustá su ruta en este script.' }

$previousPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $dbPassword
    foreach ($database in @('octano', 'octano_test')) {
        # Los nombres son constantes del proyecto; nunca se reciben como SQL del usuario.
        $exists = & $psql -X -w -h localhost -p 5432 -U $dbUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$database'"
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo conectar a PostgreSQL. Revisá el usuario y la contraseña.' }
        if ($exists -eq '1') {
            Write-Output "La base $database ya existe; se conserva."
        } else {
            & $psql -X -w -h localhost -p 5432 -U $dbUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $database;"
            if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la base $database." }
            Write-Output "Base $database creada."
        }
    }
} finally {
    $env:PGPASSWORD = $previousPassword
}
