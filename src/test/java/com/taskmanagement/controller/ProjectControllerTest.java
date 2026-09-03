package com.taskmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.ProjectPatchDto;
import com.taskmanagement.dto.ProjectRequestDto;
import com.taskmanagement.dto.ProjectResponseDto;
import com.taskmanagement.exception.ConflictException;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ProjectController}; see {@link TaskControllerTest}
 * for the rationale behind the slice setup.
 */
@WebMvcTest(controllers = ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerTest {

    private static final ProjectResponseDto SAMPLE_PROJECT = new ProjectResponseDto(
            1L, "Platform Migration", "Migrate the legacy monolith",
            42L, LocalDateTime.of(2026, 9, 3, 10, 15, 30));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
private final ProjectService projectService = null;

    @Test
    void shouldCreateProjectAndReturnLocationHeader() throws Exception {
        ProjectRequestDto request = new ProjectRequestDto("Platform Migration", "Migrate the legacy monolith", 42L);
        given(projectService.create(any(ProjectRequestDto.class))).willReturn(SAMPLE_PROJECT);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/projects/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ownerId").value(42));
    }

    @Test
    void shouldRejectAProjectWithoutNameOrOwner() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/validation-error"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field",
                        org.hamcrest.Matchers.containsInAnyOrder("name", "ownerId")));
    }

    @Test
    void shouldReturnPagedProjectsFilteredByOwner() throws Exception {
        given(projectService.findAll(eq(42L), any(Pageable.class)))
                .willReturn(PageResponse.from(new PageImpl<>(
                        List.of(SAMPLE_PROJECT), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/api/v1/projects").param("ownerId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Platform Migration"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void shouldDefaultToSortingByCreationDateDescending() throws Exception {
        given(projectService.findAll(isNull(), any(Pageable.class)))
                .willReturn(PageResponse.from(new PageImpl<>(List.of())));

        mockMvc.perform(get("/api/v1/projects")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(projectService).should().findAll(isNull(), captor.capture());

        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void shouldReplaceProject() throws Exception {
        ProjectRequestDto request = new ProjectRequestDto("Platform Migration - Phase 2", "Extended scope", 42L);
        given(projectService.replace(eq(1L), any(ProjectRequestDto.class))).willReturn(SAMPLE_PROJECT);

        mockMvc.perform(put("/api/v1/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldPatchOnlyTheSuppliedFields() throws Exception {
        given(projectService.patch(eq(1L), any(ProjectPatchDto.class))).willReturn(SAMPLE_PROJECT);

        mockMvc.perform(patch("/api/v1/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "Now also covers reporting" }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ProjectPatchDto> captor = ArgumentCaptor.forClass(ProjectPatchDto.class);
        then(projectService).should().patch(eq(1L), captor.capture());

        assertThat(captor.getValue().description()).isEqualTo("Now also covers reporting");
        assertThat(captor.getValue().name()).isNull();
        assertThat(captor.getValue().ownerId()).isNull();
    }

    @Test
    void shouldReturnConflictWhenDeletingAProjectThatStillHasTasks() throws Exception {
        willThrow(new ConflictException("Project 1 still has 7 task(s). Delete or reassign them first, "
                + "or repeat the request with ?cascade=true to remove them along with the project."))
                .given(projectService).delete(eq(1L), eq(false));

        mockMvc.perform(delete("/api/v1/projects/1"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/conflict"))
                .andExpect(jsonPath("$.title").value("Conflicting resource state"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("still has 7 task(s)")));
    }

    @Test
    void shouldPassTheCascadeFlagThroughOnDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/1").param("cascade", "true"))
                .andExpect(status().isNoContent());

        then(projectService).should().delete(1L, true);
    }

    @Test
    void shouldReturnProblemDetailWhenProjectDoesNotExist() throws Exception {
        given(projectService.findById(999L)).willThrow(ResourceNotFoundException.of("Project", 999L));

        mockMvc.perform(get("/api/v1/projects/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resource").value("Project"))
                .andExpect(jsonPath("$.identifier").value(999))
                .andExpect(jsonPath("$.instance").value("/api/v1/projects/999"));
    }

    @Test
    void shouldRejectAnUnreadableJsonBody() throws Exception {
        // Inherited from ResponseEntityExceptionHandler: still a problem document,
        // which is the point of extending it rather than reimplementing it.
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json }"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());

        then(projectService).should(org.mockito.Mockito.never()).create(any());
    }

    @Test
    void shouldRejectAnUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=Platform Migration"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        then(projectService).should(org.mockito.Mockito.never()).create(any());
    }

    @Test
    void shouldRejectANonPositiveOwnerIdFilter() throws Exception {
        mockMvc.perform(get("/api/v1/projects").param("ownerId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/validation-error"))
                .andExpect(jsonPath("$.errors[0].field").value("ownerId"));

        then(projectService).should(org.mockito.Mockito.never()).findAll(any(), any());
    }

    @Test
    void shouldNotInvokeTheServiceWhenTheCascadeFlagIsMalformed() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/1").param("cascade", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter").value("cascade"));

        then(projectService).should(org.mockito.Mockito.never()).delete(any(), anyBoolean());
    }
}
