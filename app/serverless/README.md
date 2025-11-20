# UniSync Serverless Components

Canvas LMS 동기화 및 AI 분석을 위한 서버리스 컴포넌트입니다.

> **전체 서버리스 아키텍처는 다음 문서를 참고하세요:**
> - [Canvas 동기화 설계](../../docs/features/canvas-sync.md) - Canvas Sync Lambda 상세 설계
> - [SQS 아키텍처](../../docs/design/sqs-architecture.md) - 전체 SQS 큐 목록 및 메시지 스키마
> - [시스템 아키텍처](../../docs/design/system-architecture.md) - 전체 워크플로우 및 데이터 흐름

---

## 빠른 시작

### 통합 테스트 런처 (권장)

```bash
# 1. venv 생성 및 활성화
python -m venv venv
venv\Scripts\activate  # Windows
source venv/bin/activate  # Linux/Mac

# 2. 의존성 설치
pip install -r requirements-dev.txt

# 3. 통합 테스트 런처 실행
python ../../scripts/test/test-all.py
```

**대화형 메뉴**에서 원하는 테스트 선택:
- 단위 테스트 / Canvas API / LocalStack 통합 / 모두 실행

### LocalStack에 배포 (선택사항)

```bash
# LocalStack 시작
docker-compose up -d localstack

# Lambda 배포
bash ../../scripts/infra/deploy-lambda.sh local
```

### 더 알아보기

자세한 테스트 방법은 **[TESTING.md](./TESTING.md)**를 참고하세요.

---

## 프로젝트 구조

```
serverless/
├── canvas-sync-lambda/          # Canvas API 호출
│   ├── src/
│   │   └── handler.py          # Lambda 핸들러
│   ├── tests/
│   │   └── test_canvas_handler.py     # 단위 테스트 (15개)
│   └── requirements.txt
│
├── llm-lambda/                  # LLM 분석 (Phase 3 - 향후)
│   ├── src/
│   │   └── handler.py
│   ├── tests/
│   └── requirements.txt
│
├── step-functions/              # Step Functions 정의 (Phase 2 - 향후)
│   └── canvas-sync-workflow.json
│
├── requirements-dev.txt         # 개발/테스트 의존성
├── README.md                    # 이 문서
├── TESTING.md                   # 테스트 가이드
└── CLAUDE.md                    # 서버리스 아키텍처 참조
```

---

## Lambda 함수 목록

| Lambda | 역할 | 트리거 | 상태 |
|--------|------|--------|------|
| canvas-sync-lambda | Canvas API 조회, SQS 메시지 발행 | User-Service (AWS SDK 직접 호출) | ✅ Phase 1 |
| llm-lambda | 과제 분석, 서브태스크 생성 | SQS | 💡 Phase 3 향후 |

---

## 개발 환경 설정

### 요구사항

- Python 3.11+
- Docker & Docker Compose
- LocalStack (Lambda, SQS 에뮬레이션)

### 환경 설정

```bash
# 1. venv 생성
python -m venv venv

# 2. venv 활성화
venv\Scripts\activate  # Windows
source venv/bin/activate  # Linux/Mac

# 3. 의존성 설치
pip install -r requirements-dev.txt
```

### LocalStack 시작

```bash
# 1. LocalStack 시작
docker-compose up -d localstack

# 2. Lambda 배포
bash ../../scripts/infra/deploy-lambda.sh local

# 3. 배포 확인
awslocal lambda list-functions
```

---

## 테스트

```bash
# 통합 테스트 런처 사용 (권장)
python ../../scripts/test/test-all.py

# 또는 직접 실행
cd canvas-sync-lambda
pytest tests/ -v
```

자세한 테스트 방법은 **[TESTING.md](./TESTING.md)**를 참고하세요.

---

## 필수 환경변수

환경변수 전체 목록은 [app/serverless/CLAUDE.md](./CLAUDE.md)를 참고하세요.

**Canvas Sync Lambda 주요 변수**:
- `USER_SERVICE_URL` - User-Service API URL
- `CANVAS_API_BASE_URL` - Canvas LMS URL
- `CANVAS_SYNC_API_KEY` - 내부 API 인증 키
- `AWS_REGION` - AWS 리전
- `SQS_ENDPOINT` - SQS 엔드포인트 (LocalStack: http://localhost:4566)

`.env.local.example`을 복사하여 `.env.local`로 저장하고 값을 입력하세요:

```bash
cp ../../.env.local.example ../../.env.local
```

---

## 배포

### LocalStack (로컬 개발)

```bash
# 전체 배포 스크립트
bash ../../scripts/infra/deploy-lambda.sh local
```

### AWS (프로덕션)

```bash
# TODO: SAM/Terraform/CDK 사용
bash ../../scripts/infra/deploy-lambda.sh production
```

---

## 참고 문서

**설계 문서**:
- [Canvas 동기화 설계](../../docs/features/canvas-sync.md) - Lambda 상세 설계
- [SQS 아키텍처](../../docs/design/sqs-architecture.md) - SQS 큐 및 메시지 스키마
- [시스템 아키텍처](../../docs/design/system-architecture.md) - 전체 워크플로우

**개발 가이드**:
- [TESTING.md](./TESTING.md) - 테스트 가이드
- [CLAUDE.md](./CLAUDE.md) - 환경변수 및 워크플로우 참조
- [Shared Modules](../shared/README.md) - DTO 사용법
