package com.unisync.user.group.controller;

import com.unisync.user.group.service.GroupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalGroupController.class)
@DisplayName("InternalGroupController 테스트")
class InternalGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupService groupService;

    @Test
    @DisplayName("GET /api/internal/groups/memberships/{cognitoSub} - 사용자의 그룹 ID 목록 반환")
    void getUserGroupIds() throws Exception {
        String cognitoSub = "user-123";
        given(groupService.getMyGroupIds(cognitoSub)).willReturn(List.of(1L, 2L));

        mockMvc.perform(get("/api/internal/groups/memberships/" + cognitoSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(1))
                .andExpect(jsonPath("$[1]").value(2));

        then(groupService).should().getMyGroupIds(cognitoSub);
    }
}

