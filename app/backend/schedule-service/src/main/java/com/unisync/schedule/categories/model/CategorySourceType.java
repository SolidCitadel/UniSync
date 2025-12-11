package com.unisync.schedule.categories.model;

/**
 * 카테고리 출처 구분값.
 * USER_CREATED: 사용자가 직접 만든 카테고리
 * CANVAS_COURSE: Canvas 연동으로 생성된 과목 카테고리
 * 기타 연동 소스 확장 가능 (Google, Todoist 등)
 */
public enum CategorySourceType {
    USER_CREATED,
    CANVAS_COURSE,
    GOOGLE_CALENDAR,
    TODOIST_PROJECT
}
