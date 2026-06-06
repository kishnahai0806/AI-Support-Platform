# Example local runner for support-api and ai-processor.
# Copy this file to run-local.ps1 and replace placeholder values.

$env:JWT_SECRET = "replace-with-a-local-32-plus-character-secret"
$env:JWT_ACCESS_EXPIRY_MS = "900000"
$env:JWT_REFRESH_EXPIRY_MS = "604800000"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/support_db"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "postgres"
$env:SPRING_REDIS_HOST = "localhost"
$env:SPRING_REDIS_PORT = "6379"
$env:OPENAI_API_KEY = "sk-your-openai-api-key"
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
$env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4317"

Write-Host "Usage: copy run-local.example.ps1 to run-local.ps1, fill in local values, then run .\run-local.ps1" -ForegroundColor Cyan
Write-Host "Starting support-api on port 8080..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\support-api'; .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'"

Write-Host "Starting ai-processor on port 8081..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\ai-processor'; .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'"
