# Build and Push Docker Images to ECR
# Fixed version using cmd.exe for ECR login

param(
    [string]$Region = "ap-northeast-2",
    [switch]$SkipBuild = $false
)

Write-Host "=== UniSync Docker Build & Push Script ===" -ForegroundColor Cyan
Write-Host ""

# Get AWS Account ID
Write-Host "Getting AWS Account ID..." -ForegroundColor Yellow
$AccountId = aws sts get-caller-identity --query Account --output text

if (-not $AccountId) {
    Write-Host "Error: Failed to get AWS Account ID. Make sure AWS CLI is configured." -ForegroundColor Red
    exit 1
}

Write-Host "AWS Account ID: $AccountId" -ForegroundColor Green
Write-Host ""

# ECR Login using cmd.exe (workaround for PowerShell pipe issues)
Write-Host "Logging in to ECR..." -ForegroundColor Yellow
cmd /c "aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $AccountId.dkr.ecr.$Region.amazonaws.com"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: ECR login failed" -ForegroundColor Red
    exit 1
}

Write-Host "ECR login successful!" -ForegroundColor Green
Write-Host ""

# Define services
$services = @(
    @{Name="api-gateway"; Port=8080},
    @{Name="user-service"; Port=8081},
    @{Name="course-service"; Port=8082},
    @{Name="schedule-service"; Port=8083}
)

# Build and push each service
foreach ($service in $services) {
    $serviceName = $service.Name
    $ecrRepo = "$AccountId.dkr.ecr.$Region.amazonaws.com/unisync-$serviceName"
    
    Write-Host "================================================" -ForegroundColor Cyan
    Write-Host "Processing: $serviceName" -ForegroundColor Cyan
    Write-Host "================================================" -ForegroundColor Cyan
    
    if (-not $SkipBuild) {
        # Build image
        Write-Host "Building $serviceName for ARM64..." -ForegroundColor Yellow
        docker buildx build `
            --platform linux/arm64 `
            -t "unisync-$serviceName`:latest" `
            -f "app/backend/$serviceName/Dockerfile" `
            app/
        
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Error: Build failed for $serviceName" -ForegroundColor Red
            exit 1
        }
        
        Write-Host "Build successful for $serviceName!" -ForegroundColor Green
    } else {
        Write-Host "Skipping build for $serviceName (using existing local image)" -ForegroundColor Yellow
    }
    
    # Tag image
    Write-Host "Tagging image..." -ForegroundColor Yellow
    docker tag "unisync-$serviceName`:latest" "$ecrRepo`:latest"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Tagging failed for $serviceName" -ForegroundColor Red
        exit 1
    }
    
    # Push to ECR
    Write-Host "Pushing to ECR..." -ForegroundColor Yellow
    docker push "$ecrRepo`:latest"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Push failed for $serviceName" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Successfully pushed $serviceName to ECR!" -ForegroundColor Green
    Write-Host ""
}

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "All services built and pushed successfully!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. ECS services will automatically pull the new images" -ForegroundColor White
Write-Host "2. Or force update: aws ecs update-service --cluster unisync-cluster --service unisync-<service-name> --force-new-deployment" -ForegroundColor White
Write-Host "3. Check service status: aws ecs describe-services --cluster unisync-cluster --services <service-name>" -ForegroundColor White
Write-Host "4. Access via ALB: http://unisync-alb-802813153.ap-northeast-2.elb.amazonaws.com" -ForegroundColor White
