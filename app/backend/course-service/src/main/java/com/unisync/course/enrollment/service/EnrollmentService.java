package com.unisync.course.enrollment.service;

import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.enrollment.dto.CourseDisabledEventDto;
import com.unisync.course.enrollment.dto.EnrollmentResponse;
import com.unisync.course.enrollment.dto.EnrollmentToggleRequest;
import com.unisync.course.enrollment.exception.EnrollmentNotFoundException;
import com.unisync.course.enrollment.publisher.CourseEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enrollment Service
 * 수강 관계 관리 및 동기화 활성화/비활성화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseEventPublisher courseEventPublisher;

    /**
     * 사용자의 모든 수강 목록 조회
     */
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getUserEnrollments(String cognitoSub) {
        log.info("사용자 수강 목록 조회 - cognitoSub: {}", cognitoSub);

        List<Enrollment> enrollments = enrollmentRepository.findAllByCognitoSub(cognitoSub);

        return enrollments.stream()
                .map(EnrollmentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 수강의 동기화 활성화/비활성화 토글
     *
     * @param enrollmentId 수강 ID
     * @param request 토글 요청 (isSyncEnabled)
     * @param cognitoSub 사용자 Cognito Sub (권한 검증용)
     * @return 업데이트된 수강 정보
     */
    @Transactional
    public EnrollmentResponse toggleSyncEnabled(Long enrollmentId, EnrollmentToggleRequest request, String cognitoSub) {
        log.info("수강 동기화 토글 - enrollmentId: {}, isSyncEnabled: {}, cognitoSub: {}",
                enrollmentId, request.getIsSyncEnabled(), cognitoSub);

        // 수강 조회 및 권한 확인
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException("수강 정보를 찾을 수 없습니다. ID: " + enrollmentId));

        if (!enrollment.getCognitoSub().equals(cognitoSub)) {
            throw new IllegalArgumentException("해당 수강 정보에 접근할 권한이 없습니다.");
        }

        // 이미 같은 상태면 그대로 반환
        if (enrollment.getIsSyncEnabled().equals(request.getIsSyncEnabled())) {
            log.info("이미 동일한 동기화 상태입니다 - enrollmentId: {}, isSyncEnabled: {}",
                    enrollmentId, request.getIsSyncEnabled());
            return EnrollmentResponse.from(enrollment);
        }

        // 동기화 상태 변경
        Boolean previousState = enrollment.getIsSyncEnabled();
        enrollment.setIsSyncEnabled(request.getIsSyncEnabled());

        Enrollment updated = enrollmentRepository.save(enrollment);

        log.info("✅ 동기화 상태 변경 완료 - enrollmentId: {}, {} → {}",
                enrollmentId, previousState, updated.getIsSyncEnabled());

        // 동기화 비활성화 시 Schedule-Service로 삭제 이벤트 발행
        if (!request.getIsSyncEnabled()) {
            publishCourseDisabledEvent(updated);
        }

        return EnrollmentResponse.from(updated);
    }

    /**
     * 과목 비활성화 이벤트 발행
     * Schedule-Service가 해당 과목의 모든 일정을 삭제하도록 함
     */
    private void publishCourseDisabledEvent(Enrollment enrollment) {
        CourseDisabledEventDto event = CourseDisabledEventDto.builder()
                .eventType("COURSE_DISABLED")
                .cognitoSub(enrollment.getCognitoSub())
                .courseId(enrollment.getCourse().getId())
                .canvasCourseId(enrollment.getCourse().getCanvasCourseId())
                .courseName(enrollment.getCourse().getName())
                .build();

        courseEventPublisher.publishCourseDisabledEvent(event);

        log.info("📤 Published COURSE_DISABLED event: courseId={}, cognitoSub={}",
                enrollment.getCourse().getId(), enrollment.getCognitoSub());
    }
}
