"""
LLM Lambda 단위 테스트
"""

import json
import pytest
from unittest.mock import patch, MagicMock, call


# 테스트용 환경 변수 설정
@pytest.fixture(autouse=True)
def setup_env(monkeypatch):
    """모든 테스트에 자동으로 적용되는 환경 변수"""
    monkeypatch.setenv('LLM_API_URL', 'https://api.openai.com/v1/chat/completions')
    monkeypatch.setenv('LLM_API_KEY', 'test-api-key-12345')
    monkeypatch.setenv('AWS_REGION', 'ap-northeast-2')
    monkeypatch.setenv('SQS_ENDPOINT', 'http://localhost:4566')


@pytest.fixture
def sqs_event_assignment_created():
    """테스트용 SQS 이벤트: ASSIGNMENT_CREATED"""
    return {
        'Records': [
            {
                'messageId': 'msg-001',
                'body': json.dumps({
                    'eventType': 'ASSIGNMENT_CREATED',
                    'courseId': 123,
                    'canvasAssignmentId': 'canvas_456',
                    'title': '중간고사 프로젝트',
                    'description': 'Spring Boot를 사용한 RESTful API 개발. 사용자 인증, CRUD 기능 구현 필요.',
                    'dueAt': '2025-11-15T23:59:00Z'
                })
            }
        ]
    }


@pytest.fixture
def sqs_event_submission_detected():
    """테스트용 SQS 이벤트: SUBMISSION_DETECTED"""
    return {
        'Records': [
            {
                'messageId': 'msg-002',
                'body': json.dumps({
                    'eventType': 'SUBMISSION_DETECTED',
                    'userId': 5,
                    'courseId': 123,
                    'assignmentId': 10,
                    'submissionMetadata': {
                        'submittedAt': '2025-11-14T10:30:00Z',
                        'submissionType': 'online_upload',
                        'attachments': [
                            {'filename': 'project.zip', 'url': 'https://...'}
                        ]
                    }
                })
            }
        ]
    }


@pytest.fixture
def llm_response_tasks():
    """테스트용 LLM 응답: Task 생성"""
    return json.dumps({
        'tasks': [
            {
                'title': 'Spring Boot 프로젝트 초기 설정',
                'description': 'Spring Initializr로 프로젝트 생성, 의존성 추가',
                'priority': 'HIGH',
                'subtasks': [
                    {
                        'title': 'Spring Initializr에서 프로젝트 생성',
                        'description': 'Java 21, Spring Boot 3.5, JPA, Web 의존성 선택',
                        'priority': 'HIGH'
                    },
                    {
                        'title': 'DB 연결 설정',
                        'description': 'application.yml에 MySQL 연결 정보 추가',
                        'priority': 'HIGH'
                    }
                ]
            },
            {
                'title': 'User API 구현',
                'description': '사용자 CRUD API 개발',
                'priority': 'MEDIUM',
                'subtasks': [
                    {
                        'title': 'User Entity 작성',
                        'description': 'JPA Entity 클래스 작성',
                        'priority': 'MEDIUM'
                    }
                ]
            }
        ]
    })


@pytest.fixture
def llm_response_validation():
    """테스트용 LLM 응답: 제출물 검증"""
    return json.dumps({
        'isValid': True,
        'reason': '제출물이 요구사항을 충족합니다. ZIP 파일에 소스코드와 README가 포함되어 있습니다.',
        'recommendation': '없음'
    })


class TestLambdaHandler:
    """lambda_handler 메인 함수 테스트"""

    @patch('src.handler.handle_assignment_analysis')
    def test_lambda_handler_assignment_created(
        self,
        mock_handle_analysis,
        sqs_event_assignment_created
    ):
        """ASSIGNMENT_CREATED 이벤트 처리"""
        from src.handler import lambda_handler

        # When: Lambda 실행
        result = lambda_handler(sqs_event_assignment_created, None)

        # Then: 성공 응답
        assert result['statusCode'] == 200
        assert result['body'] == 'LLM 처리 완료'

        # Then: handle_assignment_analysis 호출 확인
        mock_handle_analysis.assert_called_once()
        call_args = mock_handle_analysis.call_args[0][0]
        assert call_args['eventType'] == 'ASSIGNMENT_CREATED'
        assert call_args['title'] == '중간고사 프로젝트'

    @patch('src.handler.handle_submission_validation')
    def test_lambda_handler_submission_detected(
        self,
        mock_handle_validation,
        sqs_event_submission_detected
    ):
        """SUBMISSION_DETECTED 이벤트 처리"""
        from src.handler import lambda_handler

        # When: Lambda 실행
        result = lambda_handler(sqs_event_submission_detected, None)

        # Then: 성공 응답
        assert result['statusCode'] == 200

        # Then: handle_submission_validation 호출 확인
        mock_handle_validation.assert_called_once()
        call_args = mock_handle_validation.call_args[0][0]
        assert call_args['eventType'] == 'SUBMISSION_DETECTED'
        assert call_args['userId'] == 5

    def test_lambda_handler_unknown_event_type(self):
        """알 수 없는 이벤트 타입은 무시"""
        from src.handler import lambda_handler

        unknown_event = {
            'Records': [
                {
                    'messageId': 'msg-999',
                    'body': json.dumps({
                        'eventType': 'UNKNOWN_EVENT',
                        'data': 'test'
                    })
                }
            ]
        }

        # When: 알 수 없는 이벤트 처리
        result = lambda_handler(unknown_event, None)

        # Then: 성공 응답 (단순히 무시)
        assert result['statusCode'] == 200

    @patch('src.handler.handle_assignment_analysis')
    def test_lambda_handler_batch_processing(self, mock_handle_analysis):
        """여러 SQS 레코드 배치 처리"""
        from src.handler import lambda_handler

        # Given: 3개의 SQS 레코드
        batch_event = {
            'Records': [
                {
                    'messageId': 'msg-001',
                    'body': json.dumps({
                        'eventType': 'ASSIGNMENT_CREATED',
                        'courseId': 1,
                        'canvasAssignmentId': 'a1',
                        'title': '과제 1',
                        'description': 'desc 1',
                        'dueAt': '2025-11-01T23:59:00Z'
                    })
                },
                {
                    'messageId': 'msg-002',
                    'body': json.dumps({
                        'eventType': 'ASSIGNMENT_CREATED',
                        'courseId': 2,
                        'canvasAssignmentId': 'a2',
                        'title': '과제 2',
                        'description': 'desc 2',
                        'dueAt': '2025-11-02T23:59:00Z'
                    })
                },
                {
                    'messageId': 'msg-003',
                    'body': json.dumps({
                        'eventType': 'ASSIGNMENT_CREATED',
                        'courseId': 3,
                        'canvasAssignmentId': 'a3',
                        'title': '과제 3',
                        'description': 'desc 3',
                        'dueAt': '2025-11-03T23:59:00Z'
                    })
                }
            ]
        }

        # When: 배치 처리
        result = lambda_handler(batch_event, None)

        # Then: 3개 모두 처리
        assert result['statusCode'] == 200
        assert mock_handle_analysis.call_count == 3


class TestHandleAssignmentAnalysis:
    """handle_assignment_analysis 함수 테스트"""

    @patch('src.handler.send_to_sqs')
    @patch('src.handler.call_llm')
    def test_handle_assignment_analysis_success(
        self,
        mock_call_llm,
        mock_send_sqs,
        llm_response_tasks
    ):
        """과제 분석 및 Task 생성 성공"""
        # Given: LLM이 Task 생성 응답
        mock_call_llm.return_value = llm_response_tasks

        from src.handler import handle_assignment_analysis

        # When: 과제 분석 처리
        message = {
            'courseId': 123,
            'canvasAssignmentId': 'canvas_456',
            'title': '중간고사 프로젝트',
            'description': 'Spring Boot API 개발',
            'dueAt': '2025-11-15T23:59:00Z'
        }
        handle_assignment_analysis(message)

        # Then: LLM 호출 확인
        mock_call_llm.assert_called_once()
        prompt_arg = mock_call_llm.call_args[0][0]
        assert '중간고사 프로젝트' in prompt_arg
        assert 'Spring Boot API 개발' in prompt_arg

        # Then: SQS로 전송 확인
        mock_send_sqs.assert_called_once_with(
            'task-creation-queue',
            {
                'eventType': 'TASKS_GENERATED',
                'courseId': 123,
                'canvasAssignmentId': 'canvas_456',
                'tasks': json.loads(llm_response_tasks)['tasks']
            }
        )

    @patch('src.handler.send_to_sqs')
    @patch('src.handler.call_llm')
    def test_handle_assignment_analysis_creates_multiple_tasks(
        self,
        mock_call_llm,
        mock_send_sqs,
        llm_response_tasks
    ):
        """LLM이 여러 Task를 생성하는지 확인"""
        # Given
        mock_call_llm.return_value = llm_response_tasks

        from src.handler import handle_assignment_analysis

        # When
        message = {
            'courseId': 123,
            'canvasAssignmentId': 'canvas_456',
            'title': '프로젝트',
            'description': 'desc',
            'dueAt': '2025-11-15T23:59:00Z'
        }
        handle_assignment_analysis(message)

        # Then: 2개의 task가 생성됨
        sent_message = mock_send_sqs.call_args[0][1]
        assert len(sent_message['tasks']) == 2
        assert sent_message['tasks'][0]['title'] == 'Spring Boot 프로젝트 초기 설정'
        assert sent_message['tasks'][1]['title'] == 'User API 구현'

        # Then: Subtask도 포함됨
        assert len(sent_message['tasks'][0]['subtasks']) == 2
        assert len(sent_message['tasks'][1]['subtasks']) == 1


class TestHandleSubmissionValidation:
    """handle_submission_validation 함수 테스트"""

    @patch('src.handler.call_llm')
    def test_handle_submission_validation_valid(
        self,
        mock_call_llm,
        llm_response_validation
    ):
        """유효한 제출물 검증"""
        # Given: LLM이 유효성 확인
        mock_call_llm.return_value = llm_response_validation

        from src.handler import handle_submission_validation

        # When: 제출물 검증
        message = {
            'userId': 5,
            'courseId': 123,
            'assignmentId': 10,
            'submissionMetadata': {
                'submittedAt': '2025-11-14T10:30:00Z',
                'submissionType': 'online_upload',
                'attachments': [{'filename': 'project.zip'}]
            }
        }
        handle_submission_validation(message)

        # Then: LLM 호출 확인
        mock_call_llm.assert_called_once()
        prompt_arg = mock_call_llm.call_args[0][0]
        assert '2025-11-14T10:30:00Z' in prompt_arg
        assert 'online_upload' in prompt_arg
        assert '1개' in prompt_arg  # 첨부파일 수

    @patch('src.handler.call_llm')
    def test_handle_submission_validation_invalid(self, mock_call_llm):
        """부적합한 제출물 검증"""
        # Given: LLM이 부적합 판정
        invalid_response = json.dumps({
            'isValid': False,
            'reason': '제출물에 소스코드가 누락되었습니다.',
            'recommendation': 'README.md와 함께 전체 소스코드를 다시 제출해주세요.'
        })
        mock_call_llm.return_value = invalid_response

        from src.handler import handle_submission_validation

        # When: 제출물 검증
        message = {
            'userId': 5,
            'courseId': 123,
            'assignmentId': 10,
            'submissionMetadata': {
                'submittedAt': '2025-11-14T10:30:00Z',
                'submissionType': 'online_upload',
                'attachments': []
            }
        }
        handle_submission_validation(message)

        # Then: 유효성 검증 수행 (에러 없이 완료)
        mock_call_llm.assert_called_once()


class TestCallLlm:
    """call_llm 함수 테스트"""

    @patch('src.handler.requests.post')
    def test_call_llm_success(self, mock_requests_post):
        """LLM API 호출 성공"""
        # Given: OpenAI API 정상 응답
        mock_response = MagicMock()
        mock_response.json.return_value = {
            'choices': [
                {
                    'message': {
                        'content': '{"tasks": []}'
                    }
                }
            ]
        }
        mock_response.raise_for_status = MagicMock()
        mock_requests_post.return_value = mock_response

        from src.handler import call_llm

        # When: LLM 호출
        result = call_llm('테스트 프롬프트')

        # Then: 응답 반환
        assert result == '{"tasks": []}'

        # Then: API 호출 확인
        mock_requests_post.assert_called_once()
        call_args = mock_requests_post.call_args

        # Then: URL 확인
        assert call_args[0][0] == 'https://api.openai.com/v1/chat/completions'

        # Then: 헤더 확인
        assert call_args[1]['headers']['Authorization'] == 'Bearer test-api-key-12345'
        assert call_args[1]['headers']['Content-Type'] == 'application/json'

        # Then: Payload 확인
        payload = call_args[1]['json']
        assert payload['model'] == 'gpt-4'
        assert payload['temperature'] == 0.7
        assert len(payload['messages']) == 2
        assert payload['messages'][0]['role'] == 'system'
        assert payload['messages'][1]['role'] == 'user'
        assert payload['messages'][1]['content'] == '테스트 프롬프트'

    @patch('src.handler.requests.post')
    def test_call_llm_api_error(self, mock_requests_post):
        """LLM API 에러 시 예외 발생"""
        # Given: API 에러
        mock_response = MagicMock()
        mock_response.raise_for_status.side_effect = Exception('API rate limit exceeded')
        mock_requests_post.return_value = mock_response

        from src.handler import call_llm

        # When/Then: 예외 발생
        with pytest.raises(Exception) as exc_info:
            call_llm('테스트 프롬프트')

        assert 'API rate limit exceeded' in str(exc_info.value)


class TestSendToSqs:
    """SQS 전송 함수 테스트"""

    @patch('src.handler.sqs')
    def test_send_to_sqs_success(self, mock_sqs):
        """정상적인 SQS 메시지 전송"""
        # Given: SQS 클라이언트 mock
        mock_sqs.get_queue_url.return_value = {
            'QueueUrl': 'http://localhost:4566/000000000000/task-creation-queue'
        }
        mock_sqs.send_message.return_value = {'MessageId': 'msg-123'}

        from src.handler import send_to_sqs

        # When: 메시지 전송
        test_message = {
            'eventType': 'TASKS_GENERATED',
            'tasks': [{'title': 'Task 1'}]
        }
        send_to_sqs('task-creation-queue', test_message)

        # Then: 큐 URL 조회 확인
        mock_sqs.get_queue_url.assert_called_once_with(QueueName='task-creation-queue')

        # Then: 메시지 전송 확인
        mock_sqs.send_message.assert_called_once()
        call_args = mock_sqs.send_message.call_args
        assert call_args[1]['QueueUrl'] == 'http://localhost:4566/000000000000/task-creation-queue'

        # Then: 메시지 본문 확인
        sent_body = json.loads(call_args[1]['MessageBody'])
        assert sent_body['eventType'] == 'TASKS_GENERATED'
        assert len(sent_body['tasks']) == 1