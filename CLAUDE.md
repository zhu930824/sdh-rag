# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SDH-RAG is an enterprise-level RAG (Retrieval-Augmented Generation) knowledge base management system built with Spring AI and Vue 3. It provides intelligent Q&A, document management, knowledge retrieval, and knowledge graph capabilities.

## Commands

### Backend (Java/Spring Boot)

```bash
cd rag-backend

# Run in development mode
mvn spring-boot:run

# Run tests
mvn test

# Build JAR
mvn clean package

# Build without tests
mvn clean package -DskipTests
```

Backend runs on port **8989** by default.

### Frontend (Vue 3/TypeScript)

```bash
cd rag-frontend

# Install dependencies
npm install

# Run development server
npm run dev

# Build for production
npm run build

# Run linting
npm run lint
```

Frontend dev server runs on port **3000**, proxies `/api/*` to backend.

### Docker Deployment

```bash
# From project root
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

## Architecture

### Backend Structure (`rag-backend/`)

```
src/main/java/cn/sdh/backend/
├── common/          # Shared utilities, exceptions, context
├── config/          # Spring configurations (CORS, MyBatis, Elasticsearch, Neo4j)
├── controller/      # REST API endpoints
├── dto/             # Data Transfer Objects
├── entity/          # JPA entities (database models)
├── graph/           # Knowledge graph components (nodes, relations, extractors, repositories)
├── interceptor/     # Request interceptors (JWT authentication)
├── mapper/          # MyBatis Plus mappers
├── service/         # Business logic layer
│   └── impl/        # Service implementations
└── utils/           # Utility classes
```

### Frontend Structure (`rag-frontend/`)

```
src/
├── api/             # API client modules (axios-based)
├── components/      # Reusable Vue components
├── composables/     # Vue composition functions
├── layouts/         # Page layouts (MainLayout)
├── router/          # Vue Router configuration with auth guards
├── stores/          # Pinia state management
├── types/           # TypeScript type definitions
├── utils/           # Utility functions
└── views/           # Page components (one per route)
```

## Technology Stack

**Backend:** Java 17, Spring Boot 3.4, Spring AI, Spring AI Alibaba (DashScope/通义千问), MyBatis Plus, MySQL 8, Redis, Elasticsearch 8 (vector store), Neo4j (knowledge graph)

**Frontend:** Vue 3, TypeScript, Vite, Ant Design Vue, Pinia, Vue Router, Axios, Marked, Highlight.js

## Key Configuration

- **Environment variables:** See `.env.example` for required settings
- **Backend config:** `rag-backend/src/main/resources/application.yml`
- **Frontend proxy:** `rag-frontend/vite.config.ts` (dev server proxies `/api` to backend)
- **JWT authentication:** Configured in `application.yml` under `xushu.jwt`

## Required External Services

- MySQL 8.0+
- Redis 6.0+
- Elasticsearch 8.0+ (for vector search)
- Neo4j (for knowledge graph)
- DashScope API key (通义千问 LLM service)

## API Conventions

- Backend API base: `http://localhost:8989`
- Frontend accesses backend via `/api/*` proxy in development
- JWT token passed in `token` header/cookie
- All responses follow unified format via `common/result/` classes