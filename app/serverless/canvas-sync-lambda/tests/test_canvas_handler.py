"""
Canvas Sync Lambda Unit Tests (Phase 1: Manual Sync)
"""

import os
import json
import pytest
from unittest.mock import patch, MagicMock, call
from datetime import datetime


# Set up test environment variables
@pytest.fixture(autouse=True)
def setup_env(monkeypatch):
    """Automatically apply environment variables to all tests"""
    monkeypatch.setenv('USER_SERVICE_URL', 'http://localhost:8081')
    monkeypatch.setenv('CANVAS_API_BASE_URL', 'https://canvas.instructure.com/api/v1')
    monkeypatch.setenv('AWS_REGION', 'ap-northeast-2')
    monkeypatch.setenv('SQS_ENDPOINT', 'http://localhost:4566')
    monkeypatch.setenv('CANVAS_SYNC_API_KEY', 'test-api-key')


@pytest.fixture
def sample_event():
    """Sample event for Phase 1 (Spring direct invocation)"""
    return {
        'cognitoSub': 'test-cognito-sub-123'
    }


@pytest.fixture
def sample_event_phase2():
    """Sample event for Phase 2 (EventBridge)"""
    return {
        'detail': {
            'cognitoSub': 'test-cognito-sub-123'
        }
    }


@pytest.fixture
def sample_event_sqs():
    """Sample event for SQS invocation (optional)"""
    return {
        'Records': [
            {
                'body': json.dumps({'cognitoSub': 'test-cognito-sub-123'})
            }
        ]
    }


@pytest.fixture
def sample_courses():
    """Sample Canvas course data for testing"""
    return [
        {
            'id': 456,
            'name': 'Software Engineering',
            'course_code': 'CS401',
            'workflow_state': 'available',
            'start_at': '2025-09-01T00:00:00Z',
            'end_at': '2025-12-15T23:59:59Z'
        },
        {
            'id': 789,
            'name': 'Database Systems',
            'course_code': 'CS402',
            'workflow_state': 'available',
            'start_at': '2025-09-01T00:00:00Z',
            'end_at': '2025-12-15T23:59:59Z'
        }
    ]


@pytest.fixture
def sample_assignments():
    """Sample Canvas assignment data for testing"""
    return [
        {
            'id': 1001,
            'name': 'Midterm Project',
            'description': '<p>Develop Spring Boot web application</p>',
            'due_at': '2025-11-15T23:59:00Z',
            'points_possible': 100,
            'submission_types': ['online_upload'],
            'html_url': 'https://canvas.instructure.com/courses/456/assignments/1001',
            'created_at': '2025-09-01T10:00:00Z',
            'updated_at': '2025-09-05T15:30:00Z'
        },
        {
            'id': 1002,
            'name': 'Quiz 5',
            'description': '<p>JPA and Hibernate</p>',
            'due_at': '2025-11-20T23:59:00Z',
            'points_possible': 50,
            'submission_types': ['online_quiz'],
            'html_url': 'https://canvas.instructure.com/courses/456/assignments/1002',
            'created_at': '2025-09-10T10:00:00Z',
            'updated_at': '2025-09-12T09:00:00Z'
        }
    ]


class TestExtractCognitoSub:
    """Test extract_cognito_sub function for Phase 1/2/3 input formats"""

    def test_extract_cognito_sub_phase1(self, sample_event):
        """Test Phase 1 direct invocation format"""
        from src.handler import extract_cognito_sub

        # When: Extract from Phase 1 format
        cognito_sub = extract_cognito_sub(sample_event)

        # Then: Return correct cognitoSub
        assert cognito_sub == 'test-cognito-sub-123'

    def test_extract_cognito_sub_phase2(self, sample_event_phase2):
        """Test Phase 2 EventBridge format"""
        from src.handler import extract_cognito_sub

        # When: Extract from Phase 2 EventBridge format
        cognito_sub = extract_cognito_sub(sample_event_phase2)

        # Then: Return correct cognitoSub
        assert cognito_sub == 'test-cognito-sub-123'

    def test_extract_cognito_sub_sqs(self, sample_event_sqs):
        """Test SQS invocation format"""
        from src.handler import extract_cognito_sub

        # When: Extract from SQS format
        cognito_sub = extract_cognito_sub(sample_event_sqs)

        # Then: Return correct cognitoSub
        assert cognito_sub == 'test-cognito-sub-123'


class TestLambdaHandler:
    """Test lambda_handler main function"""

    @patch('src.handler.send_to_sqs')
    @patch('src.handler.fetch_canvas_assignments')
    @patch('src.handler.fetch_user_courses')
    @patch('src.handler.get_canvas_token')
    def test_lambda_handler_success(
        self,
        mock_get_token,
        mock_fetch_courses,
        mock_fetch_assignments,
        mock_send_sqs,
        sample_event,
        sample_courses,
        sample_assignments
    ):
        """Test successful Lambda execution (Phase 1)"""
        # Given: Canvas API responds normally
        mock_get_token.return_value = 'test-canvas-token-12345'
        mock_fetch_courses.return_value = sample_courses
        mock_fetch_assignments.return_value = sample_assignments

        # Import location is important (after environment variable setup)
        from src.handler import lambda_handler

        # When: Execute Lambda
        result = lambda_handler(sample_event, None)

        # Then: Return success response
        assert result['statusCode'] == 200
        assert result['body']['coursesCount'] == 2
        assert result['body']['assignmentsCount'] == 4  # 2 courses * 2 assignments each
        assert 'syncedAt' in result['body']

        # Then: Verify Canvas token retrieval
        mock_get_token.assert_called_once_with('test-cognito-sub-123')

        # Then: Verify Canvas API calls
        mock_fetch_courses.assert_called_once_with('test-canvas-token-12345')
        assert mock_fetch_assignments.call_count == 2
        mock_fetch_assignments.assert_any_call('test-canvas-token-12345', '456')
        mock_fetch_assignments.assert_any_call('test-canvas-token-12345', '789')

        # Then: Verify SQS sends (2 enrollments + 4 assignments)
        assert mock_send_sqs.call_count == 6

        # Then: Verify Enrollment events
        enrollment_calls = [
            call for call in mock_send_sqs.call_args_list
            if call[0][0] == 'lambda-to-courseservice-enrollments'
        ]
        assert len(enrollment_calls) == 2

        # Then: Verify enrollment message structure
        first_enrollment = enrollment_calls[0][0][1]
        assert first_enrollment['cognitoSub'] == 'test-cognito-sub-123'
        assert first_enrollment['canvasCourseId'] == 456
        assert first_enrollment['courseName'] == 'Software Engineering'

        # Then: Verify Assignment events
        assignment_calls = [
            call for call in mock_send_sqs.call_args_list
            if call[0][0] == 'lambda-to-courseservice-assignments'
        ]
        assert len(assignment_calls) == 4

        # Then: Verify assignment message structure
        first_assignment = assignment_calls[0][0][1]
        assert first_assignment['eventType'] == 'ASSIGNMENT_CREATED'
        assert first_assignment['canvasCourseId'] == 456
        assert first_assignment['canvasAssignmentId'] == 1001
        assert first_assignment['title'] == 'Midterm Project'

    @patch('src.handler.send_to_sqs')
    @patch('src.handler.fetch_canvas_assignments')
    @patch('src.handler.fetch_user_courses')
    @patch('src.handler.get_canvas_token')
    def test_lambda_handler_no_assignments(
        self,
        mock_get_token,
        mock_fetch_courses,
        mock_fetch_assignments,
        mock_send_sqs,
        sample_event,
        sample_courses
    ):
        """Test Lambda execution when no assignments exist"""
        # Given: Canvas returns courses but no assignments
        mock_get_token.return_value = 'test-canvas-token-12345'
        mock_fetch_courses.return_value = sample_courses
        mock_fetch_assignments.return_value = []  # No assignments

        from src.handler import lambda_handler

        # When: Execute Lambda
        result = lambda_handler(sample_event, None)

        # Then: Return success response
        assert result['statusCode'] == 200
        assert result['body']['coursesCount'] == 2
        assert result['body']['assignmentsCount'] == 0

        # Then: Only enrollment messages sent (2 courses)
        assert mock_send_sqs.call_count == 2

    @patch('src.handler.get_canvas_token')
    def test_lambda_handler_token_error(self, mock_get_token, sample_event):
        """Test exception when Canvas token retrieval fails"""
        # Given: Token retrieval fails
        mock_get_token.side_effect = Exception('Canvas token not found for user')

        from src.handler import lambda_handler

        # When/Then: Exception is raised
        with pytest.raises(Exception) as exc_info:
            lambda_handler(sample_event, None)

        assert 'Canvas token not found for user' in str(exc_info.value)

    @patch('src.handler.fetch_user_courses')
    @patch('src.handler.get_canvas_token')
    def test_lambda_handler_canvas_api_error(
        self,
        mock_get_token,
        mock_fetch_courses,
        sample_event
    ):
        """Test exception when Canvas API call fails"""
        # Given: Canvas API returns error
        mock_get_token.return_value = 'test-canvas-token-12345'
        mock_fetch_courses.side_effect = Exception('Canvas API error: 503 Service Unavailable')

        from src.handler import lambda_handler

        # When/Then: Exception is raised
        with pytest.raises(Exception) as exc_info:
            lambda_handler(sample_event, None)

        assert 'Canvas API error' in str(exc_info.value)


class TestGetCanvasToken:
    """Test get_canvas_token function"""

    @patch('src.handler.requests.get')
    def test_get_canvas_token_success(self, mock_requests_get):
        """Test successful token retrieval"""
        # Given: User-Service API responds normally
        mock_response = MagicMock()
        mock_response.json.return_value = {'canvasToken': 'canvas-token-abc123'}
        mock_response.raise_for_status = MagicMock()
        mock_requests_get.return_value = mock_response

        from src.handler import get_canvas_token

        # When: Retrieve token
        token = get_canvas_token(cognito_sub="test-cognito-sub-10")

        # Then: Return correct token
        assert token == 'canvas-token-abc123'

        # Then: Verify API call with correct endpoint
        mock_requests_get.assert_called_once_with(
            'http://localhost:8081/internal/v1/credentials/canvas/by-cognito-sub/test-cognito-sub-10',
            headers={'X-Api-Key': 'test-api-key'},
            timeout=5
        )

    @patch('src.handler.requests.get')
    def test_get_canvas_token_http_error(self, mock_requests_get):
        """Test exception on HTTP error"""
        # Given: User-Service returns 404
        mock_response = MagicMock()
        mock_response.raise_for_status.side_effect = Exception('404 Not Found')
        mock_requests_get.return_value = mock_response

        from src.handler import get_canvas_token

        # When/Then: Exception is raised
        with pytest.raises(Exception) as exc_info:
            get_canvas_token(cognito_sub="test-cognito-sub-999")

        assert '404 Not Found' in str(exc_info.value)


class TestFetchUserCourses:
    """Test fetch_user_courses function"""

    @patch('src.handler.requests.get')
    def test_fetch_user_courses_success(self, mock_requests_get, sample_courses):
        """Test successful course list retrieval"""
        # Given: Canvas API responds normally
        mock_response = MagicMock()
        mock_response.json.return_value = sample_courses
        mock_response.raise_for_status = MagicMock()
        mock_requests_get.return_value = mock_response

        from src.handler import fetch_user_courses

        # When: Fetch courses
        courses = fetch_user_courses('token123')

        # Then: Return course data
        assert len(courses) == 2
        assert courses[0]['name'] == 'Software Engineering'
        assert courses[1]['name'] == 'Database Systems'

        # Then: Verify API call
        mock_requests_get.assert_called_once()
        call_args = mock_requests_get.call_args
        assert '/courses' in call_args[0][0]
        assert call_args[1]['headers']['Authorization'] == 'Bearer token123'
        assert call_args[1]['params']['enrollment_type'] == 'student'
        assert call_args[1]['params']['enrollment_state'] == 'active'

    @patch('src.handler.requests.get')
    def test_fetch_user_courses_http_error(self, mock_requests_get):
        """Test exception on Canvas API error"""
        # Given: Canvas API returns error
        mock_response = MagicMock()
        mock_response.raise_for_status.side_effect = Exception('401 Unauthorized')
        mock_requests_get.return_value = mock_response

        from src.handler import fetch_user_courses

        # When/Then: Exception is raised
        with pytest.raises(Exception) as exc_info:
            fetch_user_courses('invalid-token')

        assert '401 Unauthorized' in str(exc_info.value)


class TestFetchCanvasAssignments:
    """Test fetch_canvas_assignments function"""

    @patch('src.handler.requests.get')
    def test_fetch_canvas_assignments_success(self, mock_requests_get, sample_assignments):
        """Test successful assignment list retrieval"""
        # Given: Canvas API responds normally
        mock_response = MagicMock()
        mock_response.json.return_value = sample_assignments
        mock_response.raise_for_status = MagicMock()
        mock_requests_get.return_value = mock_response

        from src.handler import fetch_canvas_assignments

        # When: Fetch assignments
        assignments = fetch_canvas_assignments('token123', '456')

        # Then: Return assignment data
        assert len(assignments) == 2
        assert assignments[0]['name'] == 'Midterm Project'
        assert assignments[1]['name'] == 'Quiz 5'

        # Then: Verify API call
        mock_requests_get.assert_called_once()
        call_args = mock_requests_get.call_args
        assert 'courses/456/assignments' in call_args[0][0]
        assert call_args[1]['headers']['Authorization'] == 'Bearer token123'

    @patch('src.handler.requests.get')
    def test_fetch_canvas_assignments_empty(self, mock_requests_get):
        """Test when course has no assignments"""
        # Given: Canvas API returns empty list
        mock_response = MagicMock()
        mock_response.json.return_value = []
        mock_response.raise_for_status = MagicMock()
        mock_requests_get.return_value = mock_response

        from src.handler import fetch_canvas_assignments

        # When: Fetch assignments
        assignments = fetch_canvas_assignments('token123', '456')

        # Then: Return empty list
        assert len(assignments) == 0


class TestSendToSqs:
    """Test SQS send function"""

    @patch('src.handler.sqs')
    def test_send_to_sqs_success(self, mock_sqs):
        """Test successful SQS message send"""
        # Given: SQS client mock
        mock_sqs.get_queue_url.return_value = {
            'QueueUrl': 'http://localhost:4566/000000000000/lambda-to-courseservice-enrollments'
        }
        mock_sqs.send_message.return_value = {'MessageId': 'msg-123'}

        from src.handler import send_to_sqs

        # When: Send message
        test_message = {
            'cognitoSub': 'test-cognito-sub-123',
            'canvasCourseId': 456,
            'courseName': 'Software Engineering'
        }
        send_to_sqs('lambda-to-courseservice-enrollments', test_message)

        # Then: Verify queue URL retrieval
        mock_sqs.get_queue_url.assert_called_once_with(QueueName='lambda-to-courseservice-enrollments')

        # Then: Verify message send
        mock_sqs.send_message.assert_called_once()
        call_args = mock_sqs.send_message.call_args
        assert call_args[1]['QueueUrl'] == 'http://localhost:4566/000000000000/lambda-to-courseservice-enrollments'

        # Then: Verify message body
        sent_body = json.loads(call_args[1]['MessageBody'])
        assert sent_body['cognitoSub'] == 'test-cognito-sub-123'
        assert sent_body['canvasCourseId'] == 456
        assert sent_body['courseName'] == 'Software Engineering'

    @patch('src.handler.sqs')
    def test_send_to_sqs_assignment_message(self, mock_sqs):
        """Test sending assignment message to SQS"""
        # Given: SQS client mock
        mock_sqs.get_queue_url.return_value = {
            'QueueUrl': 'http://localhost:4566/000000000000/lambda-to-courseservice-assignments'
        }
        mock_sqs.send_message.return_value = {'MessageId': 'msg-456'}

        from src.handler import send_to_sqs

        # When: Send assignment message
        assignment_message = {
            'eventType': 'ASSIGNMENT_CREATED',
            'canvasCourseId': 456,
            'canvasAssignmentId': 1001,
            'title': 'Midterm Project',
            'dueAt': '2025-11-15T23:59:00'
        }
        send_to_sqs('lambda-to-courseservice-assignments', assignment_message)

        # Then: Verify queue URL retrieval
        mock_sqs.get_queue_url.assert_called_once_with(QueueName='lambda-to-courseservice-assignments')

        # Then: Verify message body
        call_args = mock_sqs.send_message.call_args
        sent_body = json.loads(call_args[1]['MessageBody'])
        assert sent_body['eventType'] == 'ASSIGNMENT_CREATED'
        assert sent_body['canvasAssignmentId'] == 1001
