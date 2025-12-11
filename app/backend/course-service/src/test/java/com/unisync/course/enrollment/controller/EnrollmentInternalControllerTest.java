package com.unisync.course.enrollment.controller;

import com.unisync.course.enrollment.dto.EnabledEnrollmentResponse;
import com.unisync.course.enrollment.service.EnrollmentQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnrollmentInternalController.class)
@DisplayName("EnrollmentInternalController 테스트")
class EnrollmentInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnrollmentQueryService enrollmentQueryService;

    @Test
    @DisplayName("GET /internal/v1/enrollments/enabled - 동기화 활성 수강 목록 조회")
    void getEnabledEnrollments() throws Exception {
        String cognitoSub = "user-123";
        List<EnabledEnrollmentResponse> responses = List.of(
                EnabledEnrollmentResponse.builder()
                        .courseId(1L)
                        .canvasCourseId(101L)
                        .courseName("데이터베이스")
                        .build()
        );

        given(enrollmentQueryService.getEnabledEnrollments(cognitoSub))
                .willReturn(responses);

        mockMvc.perform(get("/internal/v1/enrollments/enabled")
                        .header("X-Cognito-Sub", cognitoSub)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseId").value(1L))
                .andExpect(jsonPath("$[0].canvasCourseId").value(101L))
                .andExpect(jsonPath("$[0].courseName").value("데이터베이스"));

        then(enrollmentQueryService).should().getEnabledEnrollments(cognitoSub);
    }
}
