"""
LLM Lambda Handler
과제 분석 및 제출물 검증을 위한 LLM 호출
"""

import json
import os
import boto3
import requests
from typing import Dict, List, Any

# 환경 변수
LLM_API_URL = os.environ.get('LLM_API_URL', 'https://api.openai.com/v1/chat/completions')
LLM_API_KEY = os.environ.get('LLM_API_KEY', '')
AWS_REGION = os.environ.get('AWS_REGION', 'ap-northeast-2')
SQS_ENDPOINT = os.environ.get('SQS_ENDPOINT', None)

# SQS 클라이언트
sqs = boto3.client('sqs', region_name=AWS_REGION, endpoint_url=SQS_ENDPOINT)


def lambda_handler(event, context):
    """
    SQS 이벤트로 트리거되는 메인 핸들러

    Input (assignment-events-queue):
        {
            "eventType": "ASSIGNMENT_CREATED",
            "courseId": 123,
            "canvasAssignmentId": "canvas_456",
            "title": "과제 1",
            "description": "...",
            "dueAt": "2025-11-30T23:59:00Z"
        }

    Input (submission-events-queue):
        {
            "eventType": "SUBMISSION_DETECTED",
            "userId": 5,
            "courseId": 123,
            "assignmentId": 10,
            "submissionMetadata": {...}
        }
    """
    try:
        # SQS 이벤트 파싱 (배치 처리 가능)
        for record in event['Records']:
            message_body = json.loads(record['body'])
            event_type = message_body.get('eventType')

            if event_type == 'ASSIGNMENT_CREATED':
                handle_assignment_analysis(message_body)
            elif event_type == 'SUBMISSION_DETECTED':
                handle_submission_validation(message_body)
            else:
                print(f"⚠️ 알 수 없는 이벤트 타입: {event_type}")

        return {'statusCode': 200, 'body': 'LLM 처리 완료'}

    except Exception as e:
        print(f"❌ 에러 발생: {str(e)}")
        raise


def handle_assignment_analysis(message: Dict[str, Any]):
    """과제 설명을 분석하여 Task/Subtask 생성"""
    print(f"🤖 과제 분석 시작: {message['title']}")

    # LLM 프롬프트 구성
    prompt = f"""
다음 과제를 분석하여 실행 가능한 task와 subtask를 JSON 형식으로 생성해주세요.

과제 제목: {message['title']}
과제 설명: {message['description']}
마감일: {message['dueAt']}

JSON 형식:
{{
  "tasks": [
    {{
      "title": "메인 task 제목",
      "description": "상세 설명",
      "priority": "HIGH",
      "subtasks": [
        {{
          "title": "subtask 1",
          "description": "...",
          "priority": "MEDIUM"
        }}
      ]
    }}
  ]
}}

규칙:
- 과제 특성에 맞게 2-5개의 subtask로 분해
- 우선순위: HIGH, MEDIUM, LOW
- 실행 가능한 구체적인 작업으로 분해
"""

    # LLM API 호출
    llm_response = call_llm(prompt)

    # 응답 파싱
    tasks_data = json.loads(llm_response)

    # SQS로 task-creation-queue에 전송
    send_to_sqs('task-creation-queue', {
        'eventType': 'TASKS_GENERATED',
        'courseId': message['courseId'],
        'canvasAssignmentId': message['canvasAssignmentId'],
        'tasks': tasks_data['tasks']
    })

    print(f"✅ 과제 분석 완료: {len(tasks_data['tasks'])}개 task 생성")


def handle_submission_validation(message: Dict[str, Any]):
    """제출물 유효성 검증"""
    print(f"🔍 제출물 검증 시작: userId={message['userId']}")

    submission_meta = message['submissionMetadata']

    # LLM 프롬프트 구성
    prompt = f"""
다음 제출물이 과제 요구사항을 충족하는지 검증해주세요.

제출 시각: {submission_meta.get('submittedAt')}
제출 유형: {submission_meta.get('submissionType')}
첨부 파일: {len(submission_meta.get('attachments', []))}개

JSON 형식으로 응답:
{{
  "isValid": true/false,
  "reason": "검증 결과 설명",
  "recommendation": "추가 조치 사항"
}}
"""

    llm_response = call_llm(prompt)
    validation_result = json.loads(llm_response)

    # 유효하면 Sync-Service로 task 상태 업데이트 요청
    if validation_result['isValid']:
        # TODO: Sync-Service API 호출하여 task 상태를 DONE으로 변경
        print(f"✅ 제출물 유효: task 상태 업데이트 필요")
    else:
        print(f"⚠️ 제출물 부적합: {validation_result['reason']}")


def call_llm(prompt: str) -> str:
    """LLM API 호출 (OpenAI/Claude 등)"""
    headers = {
        'Authorization': f'Bearer {LLM_API_KEY}',
        'Content-Type': 'application/json'
    }

    payload = {
        'model': 'gpt-4',
        'messages': [
            {'role': 'system', 'content': 'You are an AI assistant that helps students manage their assignments.'},
            {'role': 'user', 'content': prompt}
        ],
        'temperature': 0.7
    }

    response = requests.post(LLM_API_URL, headers=headers, json=payload, timeout=30)
    response.raise_for_status()

    data = response.json()
    return data['choices'][0]['message']['content']


def send_to_sqs(queue_name: str, message: Dict[str, Any]):
    """SQS 큐로 메시지 전송"""
    response = sqs.get_queue_url(QueueName=queue_name)
    queue_url = response['QueueUrl']

    sqs.send_message(
        QueueUrl=queue_url,
        MessageBody=json.dumps(message)
    )

    print(f"  → SQS 전송: {queue_name}")