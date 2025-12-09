# ECR Login Script
param(
    [string]$Region = "ap-northeast-2",
    [string]$AccountId = "377846699896"
)

Write-Host "Getting ECR login password..." -ForegroundColor Yellow
$password = aws ecr get-login-password --region $Region

if (-not $password) {
    Write-Host "Error: Failed to get ECR password" -ForegroundColor Red
    exit 1
}

Write-Host "Logging in to ECR..." -ForegroundColor Yellow
$ecrUrl = "$AccountId.dkr.ecr.$Region.amazonaws.com"

# Use echo instead of pipeline to avoid issues
$password | docker login --username AWS --password-stdin $ecrUrl

if ($LASTEXITCODE -eq 0) {
    Write-Host "Successfully logged in to ECR!" -ForegroundColor Green
} else {
    Write-Host "ECR login failed" -ForegroundColor Red
    exit 1
}
