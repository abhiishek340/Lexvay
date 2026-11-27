SHELL := /bin/bash
.DEFAULT_GOAL := help

BACKEND := backend
FRONTEND := frontend

# Testcontainers looks for /var/run/docker.sock. Colima, OrbStack and Docker Desktop may expose the
# daemon elsewhere, so resolve the socket from the active Docker context when DOCKER_HOST is not set.
DOCKER_HOST ?= $(shell docker context inspect -f '{{(index .Endpoints "docker").Host}}' 2>/dev/null)
export DOCKER_HOST
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE ?= /var/run/docker.sock

.PHONY: help db-up db-down dev dev-backend dev-frontend test backend-test frontend-test e2e seed-pdfs seed eval eval-live infra-validate

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-16s %s\n", $$1, $$2}'

db-up: ## Start PostgreSQL with docker compose and wait until healthy
	docker compose up -d --wait

db-down: ## Stop PostgreSQL (data volume is kept)
	docker compose down

dev: db-up ## Run backend (:8080) and frontend (:4200) together
	@trap 'kill 0' EXIT; \
	( cd $(BACKEND) && ./mvnw -q spring-boot:run ) & \
	( cd $(FRONTEND) && npm start ) & \
	wait

dev-backend: db-up ## Run only the backend
	cd $(BACKEND) && ./mvnw spring-boot:run

dev-frontend: ## Run only the frontend dev server
	cd $(FRONTEND) && npm start

test: backend-test frontend-test e2e ## Run every test suite (backend, frontend unit, end-to-end)

backend-test: ## Backend tests (Testcontainers needs a running Docker daemon)
	cd $(BACKEND) && ./mvnw verify

frontend-test: ## Frontend unit tests
	cd $(FRONTEND) && npm ci --no-audit --no-fund && npm test -- --watch=false

seed-pdfs: ## Regenerate the fictional seed PDFs in seed/pdf from seed/docs
	cd $(BACKEND) && ./mvnw -q compile org.codehaus.mojo:exec-maven-plugin:3.7.0:java -Dexec.mainClass=com.lexvay.seed.SeedPdfGenerator -Dexec.args="../seed/docs ../seed/pdf"

seed: db-up ## Load the fictional demo tenants (Northfield University, Westbrook College) once
	cd $(BACKEND) && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=seed

e2e: ## End-to-end tests (Playwright + axe) against an isolated seeded stack on ports 5433/8081/4300
	scripts/e2e.sh

eval: ## Score the pipeline against eval/golden.yml with the scripted model (isolated database, slot ports, no network)
	scripts/eval.sh

eval-live: ## Same with the real model; runs only when ANTHROPIC_API_KEY is set, and reports tokens and cost
	scripts/eval.sh --live

infra-validate: ## Terraform format check, validate and offline tests in infra/ (never plans or applies)
	terraform -chdir=infra fmt -check -recursive
	terraform -chdir=infra init -backend=false -input=false
	terraform -chdir=infra validate
	terraform -chdir=infra test
