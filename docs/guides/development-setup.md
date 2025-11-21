# Development Setup Guide

UniSync 개발 환경 설정 가이드입니다.

## 목차

- [사전 요구사항](#사전-요구사항)
- [Poetry 설치](#poetry-설치)
- [개발 환경 설정](#개발-환境-설정)
- [의존성 관리](#의존성-관리)
- [테스트 실행](#테스트-실행)
- [문제 해결](#문제-해결)

---

## 사전 요구사항

### 필수 설치

- **Python 3.8+**
  ```bash
  python --version  # 3.8 이상 확인
  ```

- **Poetry** (Python 의존성 관리 도구)
  - 아래 [Poetry 설치](#poetry-설치) 참고

- **Docker & Docker Compose**
  - LocalStack, MySQL 등 인프라 실행용

### 선택 설치

- **Java 21** (백엔드 서비스 개발 시)
- **AWS CLI** (배포 시)

---

## Poetry 설치

Poetry는 Python 의존성 관리 및 가상환경을 자동으로 처리합니다.

### 설치 방법

#### Linux / macOS / WSL
```bash
curl -sSL https://install.python-poetry.org | python3 -
```

#### Windows (PowerShell)
```powershell
(Invoke-WebRequest -Uri https://install.python-poetry.org -UseBasicParsing).Content | py -
```

### PATH 설정

설치 후 다음 경로를 PATH에 추가:
- Linux/macOS: `~/.local/bin`
- Windows: `%APPDATA%\Python\Scripts`

### 설치 확인

```bash
poetry --version
# Poetry (version 1.8.0) 또는 그 이상
```

---

## 개발 환경 설정

### 1. 저장소 클론

```bash
git clone https://github.com/your-org/UniSync.git
cd UniSync
```

### 2. 의존성 설치 (자동 venv 생성)

```bash
# 프로덕션 + 개발 의존성 모두 설치
poetry install

# 프로덕션 의존성만 설치
poetry install --only main
```

**Poetry가 자동으로 처리하는 것들:**
- ✅ 가상환경 자동 생성 (`.venv/` 또는 Poetry 캐시)
- ✅ 의존성 설치 및 버전 잠금 (`poetry.lock`)
- ✅ Python 버전 확인

### 3. 환경변수 설정

```bash
# 템플릿 복사
cp .env.local.example .env

# .env 파일 편집
# - LOCALSTACK_AUTH_TOKEN (필수)
# - ENCRYPTION_KEY (필수)
# - CANVAS_API_TOKEN (테스트용, 선택)
```

자세한 환경변수 설명: [environment-variables.md](./environment-variables.md)

### 4. 인프라 실행

```bash
# 개발용 인프라 실행 (LocalStack, MySQL)
docker-compose up -d

# 서비스 준비 대기 (약 1-2분)
docker-compose logs -f localstack
# "Cognito 설정 완료!" 메시지가 보일 때까지 대기
```

**참고**: Spring Boot 서비스는 IDE에서 직접 실행합니다.

---

## 의존성 관리

### 새 패키지 추가

```bash
# 프로덕션 의존성
poetry add <package-name>

# 개발 의존성
poetry add --group dev <package-name>

# 예시
poetry add boto3
poetry add --group dev pytest-cov
```

### 패키지 제거

```bash
poetry remove <package-name>
```

### 의존성 업데이트

```bash
# 모든 패키지 업데이트
poetry update

# 특정 패키지만 업데이트
poetry update <package-name>
```

### Lambda 배포용 requirements.txt 생성

AWS Lambda는 `requirements.txt`를 사용하므로, Poetry에서 export:

```bash
# Lambda용 requirements.txt 생성 (해시 제외)
poetry export -f requirements.txt --without-hashes --only main > app/serverless/canvas-sync-lambda/requirements.txt
```

---

## 테스트 실행

### Poetry로 테스트 실행

```bash
# 전체 시스템 테스트
poetry run pytest system-tests/ -v

# 특정 테스트만
poetry run pytest system-tests/infra/ -v

# 마커로 필터링
poetry run pytest -m unit
poetry run pytest -m integration
```

### 가상환경 활성화 후 실행

```bash
# 가상환경 활성화
poetry shell

# pytest 직접 실행 (venv 내부)
pytest system-tests/ -v

# 종료
exit
```

### 단위 테스트 (Lambda)

```bash
# Canvas Sync Lambda 테스트
cd app/serverless/canvas-sync-lambda
poetry run pytest tests/ -v
```

자세한 내용: [system-tests/README.md](../../system-tests/README.md)

---

## 문제 해결

### Poetry가 venv를 찾지 못함

```bash
# Poetry 설정 확인
poetry config --list

# 프로젝트 내 venv 생성하도록 설정
poetry config virtualenvs.in-project true

# venv 재생성
rm -rf .venv
poetry install
```

### 의존성 충돌

```bash
# lock 파일 재생성
poetry lock --no-update

# 의존성 다시 설치
poetry install
```

### Python 버전 불일치

```bash
# 현재 Python 버전 확인
python --version

# pyproject.toml에서 요구 버전 확인
cat pyproject.toml | grep python

# pyenv 사용 시 버전 변경
pyenv local 3.11
poetry env use python3.11
poetry install
```

### Poetry 명령어가 느림

```bash
# 병렬 설치 활성화
poetry config installer.parallel true

# 캐시 정리
poetry cache clear pypi --all
```

---

## 추가 리소스

### 관련 문서
- [Troubleshooting](./troubleshooting.md) - 일반적인 문제 해결
- [Environment Variables](./environment-variables.md) - 환경변수 상세 가이드
- [System Tests README](../../system-tests/README.md) - 테스트 구조 및 실행

### Poetry 공식 문서
- [Poetry 공식 문서](https://python-poetry.org/docs/)
- [의존성 관리](https://python-poetry.org/docs/dependency-specification/)
- [가상환경 관리](https://python-poetry.org/docs/managing-environments/)

### 프로젝트 구조
- [CLAUDE.md](../../CLAUDE.md) - 프로젝트 전체 개요
- [System Architecture](../design/system-architecture.md) - 시스템 아키텍처
