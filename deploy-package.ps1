# SDH-RAG 一键打包部署脚本
# 在项目根目录运行：.\deploy-package.ps1

param(
    [string]$OutputDir = ".\deploy-package"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SDH-RAG Docker Image Packaging Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 创建输出目录
if (Test-Path $OutputDir) {
    Write-Host "Cleaning existing output directory..." -ForegroundColor Yellow
    Remove-Item -Path "$OutputDir\*" -Recurse -Force
} else {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

# 1. 构建镜像
Write-Host "`n[1/4] Building Docker images..." -ForegroundColor Green
docker-compose build

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

# 2. 导出镜像
Write-Host "`n[2/4] Exporting images to tar files..." -ForegroundColor Green
docker save sdh-rag-rag-backend:latest -o "$OutputDir\sdh-rag-backend.tar"
docker save sdh-rag-rag-frontend:latest -o "$OutputDir\sdh-rag-frontend.tar"

# 3. 复制配置文件
Write-Host "`n[3/4] Copying configuration files..." -ForegroundColor Green

# 复制部署专用的 docker-compose.yml
Copy-Item "docker-compose.deploy.yml" -Destination "$OutputDir\docker-compose.yml"

# 创建 rag-frontend 目录并复制 nginx.conf
New-Item -ItemType Directory -Path "$OutputDir\rag-frontend" -Force | Out-Null
Copy-Item "rag-frontend\nginx.conf" -Destination "$OutputDir\rag-frontend\nginx.conf"

# 复制 .env.example 作为模板
Copy-Item ".env.example" -Destination "$OutputDir\.env.example"

# 4. 创建服务器端加载脚本
Write-Host "`n[4/4] Creating server-side script..." -ForegroundColor Green

$serverScript = @'
#!/bin/bash
# 服务器端加载镜像脚本
# 运行: chmod +x load-images.sh && ./load-images.sh

echo "Loading Docker images..."
docker load -i sdh-rag-backend.tar
docker load -i sdh-rag-frontend.tar

echo ""
echo "Images loaded:"
docker images | grep sdh-rag

echo ""
echo "============================================"
echo "Next steps:"
echo "  1. cp .env.example .env"
echo "  2. Edit .env with your configuration"
echo "  3. Run: docker-compose up -d"
echo "  4. Check logs: docker-compose logs -f"
echo "============================================"
'@

$serverScript | Out-File -FilePath "$OutputDir\load-images.sh" -Encoding UTF8

# 5. 显示文件大小和上传说明
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Packaging Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$backendSize = (Get-Item "$OutputDir\sdh-rag-backend.tar").Length / 1MB
$frontendSize = (Get-Item "$OutputDir\sdh-rag-frontend.tar").Length / 1MB

Write-Host "`nOutput directory: $OutputDir" -ForegroundColor White
Write-Host "`nFiles to upload:" -ForegroundColor White
Write-Host "  - sdh-rag-backend.tar ($([math]::Round($backendSize, 1)) MB)" -ForegroundColor Gray
Write-Host "  - sdh-rag-frontend.tar ($([math]::Round($frontendSize, 1)) MB)" -ForegroundColor Gray
Write-Host "  - docker-compose.yml" -ForegroundColor Gray
Write-Host "  - rag-frontend/nginx.conf" -ForegroundColor Gray
Write-Host "  - load-images.sh" -ForegroundColor Gray
Write-Host "  - .env.example" -ForegroundColor Gray

Write-Host "`nServer deployment steps:" -ForegroundColor Yellow
Write-Host "  1. Upload deploy-package folder to server" -ForegroundColor White
Write-Host "  2. chmod +x load-images.sh && ./load-images.sh" -ForegroundColor White
Write-Host "  3. cp .env.example .env && edit .env" -ForegroundColor White
Write-Host "  4. docker-compose up -d" -ForegroundColor White
Write-Host "  5. docker-compose logs -f (check logs)" -ForegroundColor White