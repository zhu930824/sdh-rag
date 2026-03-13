#!/bin/bash

# SDH-RAG 部署脚本
# 用于快速部署整个项目

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查 Docker 是否安装
check_docker() {
    log_info "检查 Docker 安装状态..."
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi
    log_info "Docker 已安装: $(docker --version)"
}

# 检查 Docker Compose 是否安装
check_docker_compose() {
    log_info "检查 Docker Compose 安装状态..."
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi
    if command -v docker-compose &> /dev/null; then
        log_info "Docker Compose 已安装: $(docker-compose --version)"
    else
        log_info "Docker Compose 已安装: $(docker compose version)"
    fi
}

# 检查环境变量文件
check_env_file() {
    log_info "检查环境变量配置..."
    if [ ! -f .env ]; then
        log_warn ".env 文件不存在，从 .env.example 创建..."
        cp .env.example .env
        log_warn "请编辑 .env 文件，配置必要的环境变量（特别是 DASHSCOPE_API_KEY）"
        read -p "是否现在编辑 .env 文件？(y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            ${EDITOR:-nano} .env
        fi
    fi

    # 检查必要的环境变量
    if grep -q "your_dashscope_api_key_here" .env; then
        log_error "请先在 .env 文件中配置 DASHSCOPE_API_KEY"
        exit 1
    fi
    log_info "环境变量配置检查通过"
}

# 停止现有服务
stop_services() {
    log_info "停止现有服务..."
    if command -v docker-compose &> /dev/null; then
        docker-compose down
    else
        docker compose down
    fi
}

# 构建镜像
build_images() {
    log_info "构建 Docker 镜像..."
    if command -v docker-compose &> /dev/null; then
        docker-compose build
    else
        docker compose build
    fi
}

# 启动服务
start_services() {
    log_info "启动服务..."
    if command -v docker-compose &> /dev/null; then
        docker-compose up -d
    else
        docker compose up -d
    fi
}

# 等待服务启动
wait_for_services() {
    log_info "等待服务启动..."
    sleep 10

    # 检查服务状态
    log_info "检查服务状态..."
    if command -v docker-compose &> /dev/null; then
        docker-compose ps
    else
        docker compose ps
    fi
}

# 显示访问信息
show_access_info() {
    echo ""
    log_info "=========================================="
    log_info "部署完成！"
    log_info "=========================================="
    echo ""
    log_info "访问地址："
    echo "  - 前端: http://localhost"
    echo "  - 后端 API: http://localhost:8989"
    echo "  - Elasticsearch: http://localhost:9200"
    echo ""
    log_info "默认账号："
    echo "  - 用户名: admin"
    echo "  - 密码: 123456"
    echo ""
    log_info "查看日志："
    if command -v docker-compose &> /dev/null; then
        echo "  docker-compose logs -f"
    else
        echo "  docker compose logs -f"
    fi
    echo ""
    log_info "停止服务："
    if command -v docker-compose &> /dev/null; then
        echo "  docker-compose down"
    else
        echo "  docker compose down"
    fi
    echo ""
    log_info "=========================================="
}

# 主函数
main() {
    echo ""
    log_info "SDH-RAG 部署脚本"
    log_info "=========================================="
    echo ""

    # 检查依赖
    check_docker
    check_docker_compose
    check_env_file

    # 部署
    stop_services
    build_images
    start_services
    wait_for_services
    show_access_info
}

# 执行主函数
main
