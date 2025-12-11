package com.unisync.course.enrollment.service;

import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.course.enrollment.dto.EnabledEnrollmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enrollment 조회 전용 서비스 (내부 API)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentQueryService {

    private final EnrollmentRepository enrollmentRepository;

    /**
     * 동기화 활성화된 수강 목록 조회
     */
    @Transactional(readOnly = true)
    public List<EnabledEnrollmentResponse> getEnabledEnrollments(String cognitoSub) {
        log.info("Fetching enabled enrollments for cognitoSub={}", cognitoSub);

        return enrollmentRepository.findAllByCognitoSubAndIsSyncEnabled(cognitoSub)
                .stream()
                .map(EnabledEnrollmentResponse::from)
                .collect(Collectors.toList());
    }
}
