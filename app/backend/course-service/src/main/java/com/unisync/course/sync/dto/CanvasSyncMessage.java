package com.unisync.course.sync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Canvas 통합 동기화 메시지
 * Lambda가 발행하는 단일 메시지 (모든 courses + assignments 포함)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanvasSyncMessage {

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("cognitoSub")
    private String cognitoSub;

    @JsonProperty("syncedAt")
    private String syncedAt;

    @JsonProperty("syncMode")
    private String syncMode;

    // 배열 형식 (CANVAS_SYNC_COMPLETED)
    @JsonProperty("courses")
    private List<CourseData> courses;

    // 단일 형식 (CANVAS_COURSE_SYNCED)
    @JsonProperty("course")
    private CourseData course;

    /**
     * Course 데이터 (assignments 포함)
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CourseData {

        @JsonProperty("canvasCourseId")
        private Long canvasCourseId;

        @JsonProperty("courseName")
        private String courseName;

        @JsonProperty("courseCode")
        private String courseCode;

        @JsonProperty("workflowState")
        private String workflowState;

        @JsonProperty("startAt")
        private String startAt;

        @JsonProperty("endAt")
        private String endAt;

        @JsonProperty("assignments")
        private List<AssignmentData> assignments;
    }

    /**
     * Assignment 데이터
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignmentData {

        @JsonProperty("canvasAssignmentId")
        private Long canvasAssignmentId;

        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("dueAt")
        private String dueAt;

        @JsonProperty("pointsPossible")
        private Double pointsPossible;

        @JsonProperty("submissionTypes")
        private String submissionTypes;

        @JsonProperty("htmlUrl")
        private String htmlUrl;

        @JsonProperty("createdAt")
        private String createdAt;

        @JsonProperty("updatedAt")
        private String updatedAt;
    }
}
