# UniSync Shared Modules

서비스 간 공유하는 메시지 스키마와 DTO 정의

## 디렉토리 구조

```
shared/
├── java-common/              # Java 공용 모듈
│   ├── build.gradle.kts
│   └── src/main/java/com/unisync/shared/dto/sqs/
│       └── AssignmentEventMessage.java
├── python-common/            # Python 공용 모듈
│   ├── setup.py
│   └── unisync_shared/dto/
│       └── assignment_event.py
└── message-schemas/          # JSON Schema 정의 (선택)
    └── assignment-events.schema.json
```

## 사용 방법

### Java (Spring Boot Services)

**1. settings.gradle.kts에 공유 모듈 포함**

```kotlin
rootProject.name = "your-service"

// 공유 모듈 포함
includeBuild("../../shared/java-common")
```

**2. build.gradle.kts에 의존성 추가**

```kotlin
dependencies {
    // Shared Common Module
    implementation("com.unisync:java-common:1.0.0")

    // ... 기타 의존성
}
```

**3. 코드에서 사용**

```java
import com.unisync.shared.dto.sqs.AssignmentEventMessage;

@SqsListener(value = "assignment-events-queue")
public void receiveEvent(AssignmentEventMessage message) {
    // 메시지 처리
}
```

### Python (Lambda Functions)

**1. requirements.txt에 추가**

```txt
-e ../../shared/python-common
```

또는 직접 설치:

```bash
pip install -e ../shared/python-common
```

**2. 코드에서 사용**

```python
from unisync_shared.dto import AssignmentEventMessage
import json

def lambda_handler(event, context):
    for record in event['Records']:
        message_data = json.loads(record['body'])
        message = AssignmentEventMessage(**message_data)

        print(f"Received: {message.event_type} for {message.canvas_assignment_id}")
```

## 메시지 스키마 목록

### 1. AssignmentEventMessage

**Queue**: `assignment-events-queue`
**Publisher**: Canvas-Sync-Lambda
**Consumers**: course-service, schedule-service

**스키마**:

```json
{
  "eventType": "ASSIGNMENT_CREATED | ASSIGNMENT_UPDATED",
  "canvasAssignmentId": 123456,
  "canvasCourseId": 789,
  "title": "중간고사 프로젝트",
  "description": "Spring Boot로 REST API 구현",
  "dueAt": "2025-11-15T23:59:59",
  "pointsPossible": 100,
  "submissionTypes": "online_upload",
  "createdAt": "2025-11-02T10:00:00",
  "updatedAt": "2025-11-02T10:00:00"
}
```

**이벤트 타입**:
- `ASSIGNMENT_CREATED`: 새로운 과제 생성
- `ASSIGNMENT_UPDATED`: 기존 과제 수정

## 새로운 메시지 추가 시

1. **Java DTO 작성** (`shared/java-common/src/main/java/com/unisync/shared/dto/sqs/`)
2. **Python DTO 작성** (`shared/python-common/unisync_shared/dto/`)
3. **JSON Schema 작성** (`shared/message-schemas/`)
4. **이 README 업데이트** (메시지 스키마 목록에 추가)

## 주의사항

### 호환성 유지

- Java는 **camelCase** 사용
- Python은 **snake_case** 사용
- Pydantic `alias` 설정으로 JSON 직렬화 시 camelCase로 변환
- 필드 추가 시 **Optional**로 설정하여 하위 호환성 보장

### 버전 관리

- 기존 필드는 삭제하지 말고 `@Deprecated` 처리
- Breaking Change 시 새로운 메시지 타입 생성
- Git 태그로 버전 관리 (`shared/v1.0.0`)

## 참고 문서

- [설계서](../../설계서.md) - SQS 메시지 명세 전체
- [CLAUDE.md](../../CLAUDE.md) - 프로젝트 개요 및 아키텍처