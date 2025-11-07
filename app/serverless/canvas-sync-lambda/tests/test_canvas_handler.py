"""
Canvas Sync Lambda Unit Tests
"""

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
    monkeypatch.setenv('SERVICE_AUTH_TOKEN', 'test-token')


@pytest.fixture
def sample_event():
    """Sample Step Functions event for testing"""
    return {
        'courseId': 123,
        'canvasCourseId': 'canvas_456',
        'leaderUserId': 5,
        'lastSyncedAt': '2025-10-29T12:00:00Z'
    }


@pytest.fixture
def sample_canvas_assignments():
    """Sample Canvas assignment data for testing"""
    return [
        {
            'id': 1001,
            'name': 'Midterm Project',
            'description': '<p>Develop Spring Boot web application</p>',
            'due_at': '2025-11-15T23:59:00Z',
            'points_possible': 100,
            'submission_types': ['online_upload'],
            'html_url': 'https://canvas.instructure.com/courses/456/assignments/1001'
        },
        {
            'id': 1002,
            'name': 'Quiz 5',
            'description': '<p>JPA and Hibernate</p>',
            'due_at': '2025-11-20T23:59:00Z',
            'points_possible': 50,
            'submission_types': ['online_quiz'],
            'html_url': 'https://canvas.instructure.com/courses/456/assignments/1002'
        }
    ]


@pytest.fixture
def sample_canvas_submissions():
    """Sample Canvas submission data for testing"""
    return [
        {
            'assignment_id': 1001,
            'user_id': 5,
            'workflow_state': 'submitted',
            'submitted_at': '2025-11-14T10:30:00Z',
            'submission_type': 'online_upload',
            'attachments': [
                {'filename': 'project.zip', 'url': 'https://...'}
            ]
        }
    ]


class TestLambdaHandler:
    """Test lambda_handler main function"""

    @patch('src.handler.send_to_sqs')
    @patch('src.handler.fetch_canvas_submissions')
    @patch('src.handler.fetch_canvas_announcements')
    @patch('src.handler.fetch_canvas_assignments')
    @patch('src.handler.get_canvas_token')
    def test_lambda_handler_success(
        self,
        mock_get_token,
        mock_fetch_assignments,
        mock_fetch_announcements,
        mock_fetch_submissions,
        mock_send_sqs,
        sample_event,
        sample_canvas_assignments,
        sample_canvas_submissions
    ):
        """Test successful Lambda execution"""
        # Given: Canvas API responds normally
        mock_get_token.return_value = 'test-canvas-token-12345'
        mock_fetch_assignments.return_value = sample_canvas_assignments
        mock_fetch_announcements.return_value = []
        mock_fetch_submissions.return_value = sample_canvas_submissions

        # Import location is important (after environment variable setup)
        from src.handler import lambda_handler

        # When: Execute Lambda
        result = lambda_handler(sample_event, None)

        # Then: Return success response
        assert result['statusCode'] == 200
        assert result['body']['courseId'] == 123
        assert result['body']['assignmentsCount'] == 2
        assert result['body']['submissionsCount'] == 1
        assert result['body']['eventsSent'] == 3  # 2 assignments + 1 submission

        # Then: Verify Canvas token retrieval
        mock_get_token.assert_called_once_with(5)

        # Then: Verify Canvas API calls
        mock_fetch_assignments.assert_called_once_with(
            'test-canvas-token-12345',
            'canvas_456',
            '2025-10-29T12:00:00Z'
        )

        # Then: Verify SQS sends (2 assignments + 1 submission)
        assert mock_send_sqs.call_count == 3

        # Then: Verify Assignment events
        assignment_calls = [
            call for call in mock_send_sqs.call_args_list
            if call[0][0] == 'assignment-events-queue'
        ]
        assert len(assignment_calls) == 2

        # Then: Verify Submission events
        submission_calls = [
            call for call in mock_send_sqs.call_args_list
            if call[0][0] == 'submission-events-queue'
        ]
        assert len(submission_calls) == 1

    @patch('src.handler.get_canvas_token')
    def test_lambda_handler_token_error(self, mock_get_token, sample_event):
        """Test exception when Canvas token retrieval fails"""
        # Given: Token retrieval fails
        mock_get_token.side_effect = Exception('User not found')

        from src.handler import lambda_handler

        # When/Then: Exception is raised
        with pytest.raises(Exception) as exc_info:
            lambda_handler(sample_event, None)

        assert 'User not found' in str(exc_info.value)


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

        # Then: Verify API call
        mock_requests_get.assert_called_once_with(
            'http://localhost:8081/credentials/canvas/by-cognito-sub/test-cognito-sub-10',
            headers={'X-Api-Key': 'local-dev-token'},
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


class TestCanvasApiFunctions:
    """Test Canvas API call functions"""

    @patch('src.handler.requests.get')
    def test_fetch_canvas_assignments_success(self, mock_requests_get, sample_canvas_assignments):
        """Test successful assignment list retrieval"""
        # Given: Canvas API responds normally
        mock_response = MagicMock()
        mock_response.json.return_value = sample_canvas_assignments
        mock_response.raise_for_status = MagicMock()
        mock_requests_get.return_value = mock_response

        from src.handler import fetch_canvas_assignments

        # When: Fetch assignments
        assignments = fetch_canvas_assignments('token123', 'course_456')

        # Then: Return assignment data
        assert len(assignments) == 2
        assert assignments[0]['name'] == 'Midterm Project'

        # Then: Verify API call
        mock_requests_get.assert_called_once()
        call_args = mock_requests_get.call_args
        assert 'courses/course_456/assignments' in call_args[0][0]
        assert call_args[1]['headers']['Authorization'] == 'Bearer token123'

    @patch('src.handler.requests.get')
    def test_fetch_canvas_assignments_with_since(self, mock_requests_get):
        """Test incremental sync: verify updated_since parameter"""
        # Given
        mock_response = MagicMock()
        mock_response.json.return_value = []
        mock_response.raise_for_status = MagicMock()
        mock_requests_get.return_value = mock_response

        from src.handler import fetch_canvas_assignments

        # When: Include since parameter
        fetch_canvas_assignments('token123', 'course_456', since='2025-10-29T12:00:00Z')

        # Then: Verify updated_since parameter is passed
        call_args = mock_requests_get.call_args
        assert call_args[1]['params']['updated_since'] == '2025-10-29T12:00:00Z'

    @patch('src.handler.requests.get')
    def test_fetch_canvas_submissions_filters_submitted(self, mock_requests_get):
        """Test filtering only submitted items"""
        # Given: Submissions with various states
        all_submissions = [
            {'assignment_id': 1, 'workflow_state': 'submitted'},
            {'assignment_id': 2, 'workflow_state': 'unsubmitted'},
            {'assignment_id': 3, 'workflow_state': 'submitted'},
            {'assignment_id': 4, 'workflow_state': 'graded'}
        ]
        mock_response = MagicMock()
        mock_response.json.return_value = all_submissions
        mock_response.raise_for_status = MagicMock()
        mock_requests_get.return_value = mock_response

        from src.handler import fetch_canvas_submissions

        # When: Fetch submissions
        submissions = fetch_canvas_submissions('token123', 'course_456', user_id=5)

        # Then: Return only submitted state
        assert len(submissions) == 2
        assert all(s['workflow_state'] == 'submitted' for s in submissions)


class TestSendToSqs:
    """Test SQS send function"""

    @patch('src.handler.sqs')
    def test_send_to_sqs_success(self, mock_sqs):
        """Test successful SQS message send"""
        # Given: SQS client mock
        mock_sqs.get_queue_url.return_value = {
            'QueueUrl': 'http://localhost:4566/000000000000/test-queue'
        }
        mock_sqs.send_message.return_value = {'MessageId': 'msg-123'}

        from src.handler import send_to_sqs

        # When: Send message
        test_message = {
            'eventType': 'TEST_EVENT',
            'data': 'test data'
        }
        send_to_sqs('test-queue', test_message)

        # Then: Verify queue URL retrieval
        mock_sqs.get_queue_url.assert_called_once_with(QueueName='test-queue')

        # Then: Verify message send
        mock_sqs.send_message.assert_called_once()
        call_args = mock_sqs.send_message.call_args
        assert call_args[1]['QueueUrl'] == 'http://localhost:4566/000000000000/test-queue'

        # Then: Verify message body
        sent_body = json.loads(call_args[1]['MessageBody'])
        assert sent_body['eventType'] == 'TEST_EVENT'
        assert sent_body['data'] == 'test data'