$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker is missing. Install/start Docker, or follow the native setup in README.md.'
}
if (-not (Test-Path '.env')) {
    throw 'Copy .env.example to .env and set your database and admin passwords first.'
}
docker compose up --build -d --wait
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed. Check docker compose logs app db.' }
Write-Host 'Application started. Open the localhost URL using RAILWAY_HTTP_PORT from .env (default 8080): /railway-ticket-demo/'
