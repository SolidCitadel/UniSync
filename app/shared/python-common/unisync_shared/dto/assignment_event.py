"""
Assignment Event Message DTO
Canvas-Sync-Lambda가 발행하는 Assignment 이벤트 메시지

SQS Queue: assignment-events-queue
"""

from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field


class AssignmentEventMessage(BaseModel):
    """
    Assignment 이벤트 메시지
    Java의 com.unisync.shared.dto.sqs.AssignmentEventMessage와 동일한 스키마
    """

    event_type: str = Field(..., description="이벤트 타입: ASSIGNMENT_CREATED, ASSIGNMENT_UPDATED", alias="eventType")
    canvas_assignment_id: int = Field(..., description="Canvas Assignment ID", alias="canvasAssignmentId")
    canvas_course_id: int = Field(..., description="Canvas Course ID", alias="canvasCourseId")
    title: str = Field(..., description="과제 제목")
    description: Optional[str] = Field(None, description="과제 설명")
    due_at: Optional[datetime] = Field(None, description="마감일", alias="dueAt")
    points_possible: Optional[int] = Field(None, description="배점", alias="pointsPossible")
    submission_types: Optional[str] = Field(None, description="제출 유형", alias="submissionTypes")
    created_at: Optional[datetime] = Field(None, description="Canvas에서 생성된 시간", alias="createdAt")
    updated_at: Optional[datetime] = Field(None, description="Canvas에서 마지막 수정된 시간", alias="updatedAt")

    class Config:
        # CamelCase로 직렬화 (Java와 호환)
        populate_by_name = True
        json_schema_extra = {
            "example": {
                "eventType": "ASSIGNMENT_CREATED",
                "canvasAssignmentId": 123456,
                "canvasCourseId": 789,
                "title": "중간고사 프로젝트",
                "description": "Spring Boot로 REST API 구현",
                "dueAt": "2025-11-15T23:59:59",
                "pointsPossible": 100,
                "submissionTypes": "online_upload",
                "createdAt": "2025-11-02T10:00:00",
                "updatedAt": "2025-11-02T10:00:00",
            }
        }