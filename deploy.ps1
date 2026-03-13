# SDH-RAG 部署脚本（Windows PowerShell）
# 用于快速部署整个项目

# 颜色定义
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

function Log-Info {
    Write-ColorOutput Green "[INFO] $args"
}

function Log-Warn {
    Write-ColorOutput Yellow "[WARN] $args"
}

function Log-Error {
    Write-ColorOutput Red "[ERROR] $args"
}

# 检查 Docker 是否安装
function Check-Docker {
    Log-Info "检查 Docker 安装状态..."
    try {
        $dockerVersion = docker --version
        Log-Info "Docker 已安装: $dockerVersion"
    } catch {
        Log-Error "Docker 未安装，请先安装 Docker Desktop"
        exit 1
    }
}

# 检查 Docker Compose 是否安装
function Check-DockerCompose {
    Log-Info "检查 Docker Compose 安装状态..."
    try {
        $composeVersion = docker-compose --version
        Log-Info "Docker Compose 已安装: $composeVersion"
    } catch {
        try {
            $composeVersion = docker compose version
            Log-Info "Docker Compose 已安装: $composeVersion"
        } catch {
            Log-Error "Docker Compose 未安装，请先安装 Docker Compose"
            exit 1
        }
    }
}

# 检查环境变量文件
function Check-EnvFile {
    Log-Info "检查环境变量配置..."
    if (-not (Test-Path .env)) {
        Log-Warn ".env 文件不存在，从 .env.example 创建..."
        Copy-Item .env.example .env
        Log-Warn "请编辑 .env 文件，配置必要的环境变量（特别是 DASHSCOPE_API_KEY）"
        $edit = Read-Host "是否现在编辑 .env 文件？(y/n)"
        if ($edit -eq 'y' -or $edit -eq 'Y') {
            notepad .env
        }
    }

    # 检查必要的环境变量
    $envContent = Get-Content .env -Raw
    if ($envContent -match "your_dashscope_api_key_here") {
        Log-Error "请先在 .env 文件中配置 DASHSCOPE_API_KEY"
        exit 1
    }
    Log-Info "环境变量配置检查通过"
}

# 停止现有服务
function Stop-Services {
    Log-Info "停止现有服务..."
    try {
        docker-compose down
    } catch {
        docker compose down
    }
}

# 构建镜像
function Build-Images {
    Log-Info "构建 Docker 镜像..."
    try {
        docker-compose build
    } catch {
        docker compose build
    }
}

# 启动服务
function Start-Services {
    Log-Info "启动服务..."
    try {
        docker-compose up -d
    } catch {
        docker compose up -d
    }
}

# 等待服务启动
function Wait-ForServices {
    Log-Info "等待服务启动..."
    Start-Sleep -Seconds 10

    # 检查服务状态
    Log-Info "检查服务状态..."
    try {
        docker-compose ps
    } catch {
        docker compose ps
    }
}

# 显示访问信息
function Show-AccessInfo {
    Write-Host ""
    Log-Info "=========================================="
    Log-Info "部署完成！"
    Log-Info "=========================================="
    Write-Host ""
    Log-Info "访问地址："
    Write-Host "  - 前端: http://localhost"
    Write-Host "  - 后端 API: http://localhost:8989"
    Write-Host "  - Elasticsearch: http://localhost:9200"
    Write-Host ""
    Log-Info "默认账号："
    Write-Host "  - 用户名: admin"
    Write-Host "  - 密码: 123456"
    Write-Host ""
    Log-Info "查看日志："
    Write-Host "  docker-compose logs -f"
    Write-Host ""
    Log-Info "停止服务："
    Write-Host "  docker-compose down"
    Write-Host ""
    Log-Info "=========================================="
}

# 主函数
function Main {
    Write-Host ""
    Log-Info "SDH-RAG 部署脚本"
    Log-Info "=========================================="
    Write-Host ""

    # 检查依赖
    Check-Docker
    Check-DockerCompose
    Check-EnvFile

    # 部署
    Stop-Services
    Build-Images
    Start-Services
    Wait-ForServices
    Show-AccessInfo
}

# 执行主函数
Main
