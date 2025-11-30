package com.unisync.course.common.repository;

public interface AssignmentProjection {
    Long getAssignmentId();
    Long getCanvasAssignmentId();
    Long getCanvasCourseId();
    Long getCourseId();
    String getCourseName();
    String getTitle();
    String getDescription();
    String getDueAt();
    Double getPointsPossible();
}
