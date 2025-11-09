# 개발 스크립트 (scripts/dev)

이 디렉토리는 로컬 개발 환경 설정을 위한 유틸리티 스크립트를 포함합니다.

## sync-local-config.py

**목적**: 루트 `.env` 파일의 환경변수를 각 서비스의 `application-local.yml` 파일에 자동으로 동기화

### 사용법

```bash
# 프로젝트 루트에서 실행
python scripts/dev/sync-local-config.py
```

### 동작 방식

1. **환경변수 읽기**: 루트 `.env` 파일에서 모든 환경변수 로드
2. **서비스별 매핑**: 각 서비스에 필요한 환경변수를 매핑 테이블(`SERVICE_CONFIG_MAP`)에 따라 업데이트
3. **YAML 업데이트**: YAML 형식과 주석을 유지하면서 값만 교체
4. **검증**: 업데이트 성공 여부 확인 및 결과 출력

### 동기화되는 환경변수

#### user-service
- MySQL 비밀번호 (`MYSQL_PASSWORD`)
- Cognito User Pool ID, Client ID
- SQS 큐: user-token-registered
- 암호화 키 (`ENCRYPTION_KEY`)
- API 키: Canvas Sync Lambda, LLM Lambda
- Canvas Base URL

#### course-service
- MySQL 비밀번호
- SQS 큐: assignment-events, submission-events

#### schedule-service
- MySQL 비밀번호
- SQS 큐: assignment-events, task-creation

#### api-gateway
- Cognito User Pool ID

### 언제 실행해야 하나?

- **최초 개발 환경 설정 시**
- **LocalStack 재시작 후** (Cognito User Pool ID가 변경될 수 있음)
- **`.env` 파일의 환경변수가 변경되었을 때**

### 출력 예시

```
============================================================
LocalStack 설정 동기화 스크립트
============================================================

[INFO] .env 파일 읽는 중: C:\...\UniSync\.env
[OK] User Pool ID: ap-northeast-2_xxxxx
[OK] Client ID: xxxxx
[OK] 환경변수 36개 로드됨

[INFO] 서비스 설정 파일 업데이트 중...

  [UPDATE] user-service
    [OK] password: unisync_password
    [OK] user-pool-id: ap-northeast-2_xxxxx
    [OK] client-id: xxxxx
    ...

  [UPDATE] course-service
    [OK] password: unisync_password
    [OK] assignment-events: assignment-events-queue
    ...

[SUCCESS] 4개 서비스 설정 파일 업데이트 완료!
============================================================
```

### 새로운 환경변수 추가하기

서비스에 새로운 환경변수를 추가하려면:

1. **`.env` 파일에 추가**
   ```bash
   NEW_CONFIG_VALUE=your-value-here
   ```

2. **`sync-local-config.py`의 `SERVICE_CONFIG_MAP` 업데이트**
   ```python
   SERVICE_CONFIG_MAP = {
       "user-service": [
           # 기존 매핑...
           ("new-config-key", "NEW_CONFIG_VALUE"),  # 추가
       ],
   }
   ```

3. **`application-local.yml.example` 업데이트**
   ```yaml
   some:
     section:
       new-config-key: placeholder-value  # 동기화 대상
   ```

4. **스크립트 실행**
   ```bash
   python scripts/dev/sync-local-config.py
   ```

### 문제 해결

**Q: "application-local.yml 없음" 메시지가 뜹니다**
```bash
# application-local.yml.example을 복사하세요
cd app/backend/{service}/src/main/resources
cp application-local.yml.example application-local.yml
```

**Q: 값이 업데이트되지 않습니다**
- YAML 키 이름이 정확한지 확인 (`SERVICE_CONFIG_MAP` 참조)
- `.env` 파일에 해당 환경변수가 존재하는지 확인
- YAML 파일의 들여쓰기가 올바른지 확인

**Q: LocalStack User Pool ID가 계속 변경됩니다**
- LocalStack의 `PERSISTENCE=1` 설정이 켜져 있는지 확인 (`docker-compose.yml`)
- `localstack-data` 볼륨이 삭제되지 않았는지 확인
