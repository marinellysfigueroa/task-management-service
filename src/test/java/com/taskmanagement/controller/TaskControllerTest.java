package com.taskmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.TaskFilter;
import com.taskmanagement.dto.TaskPatchDto;
import com.taskmanagement.dto.TaskRequestDto;
import com.taskmanagement.dto.TaskResponseDto;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.TaskStatus;
import com.taskmanagement.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link TaskController}.
 *
 * <p>A slice test with a mocked service is deliberate: what is under test here
 * is the HTTP contract — status codes, the Location header, query-parameter
 * binding and the shape of the RFC 7807 error documents — not the business
 * rules, which are exercised elsewhere. Security filters are switched off so a
 * failing assertion points at the controller rather than at authentication.
 */
@WebMvcTest(controllers = TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaskControllerTest {

    private static final TaskResponseDto SAMPLE_TASK = new TaskResponseDto(
            10L, "Set up the CI pipeline", "Configure build and test stages",
            TaskStatus.IN_PROGRESS, TaskPriority.HIGH, 1L, 42L, LocalDate.of(2026, 12, 31));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    @Test
    void shouldCreateTaskAndReturnLocationHeader() throws Exception {
        TaskRequestDto request = new TaskRequestDto(
                "Set up the CI pipeline", "Configure build and test stages",
                TaskStatus.TODO, TaskPriority.HIGH, 1L, 42L, LocalDate.of(2026, 12, 31));
        given(taskService.create(any(TaskRequestDto.class))).willReturn(SAMPLE_TASK);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/tasks/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void shouldRejectInvalidPayloadWithStructuredProblemDetail() throws Exception {
        // Blank title and missing projectId: two distinct field violations.
        String invalidPayload = """
                { "title": "   ", "description": "no project reference" }
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/tasks"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field",
                        org.hamcrest.Matchers.containsInAnyOrder("title", "projectId")));
    }

    @Test
    void shouldReturnProblemDetailWhenTaskDoesNotExist() throws Exception {
        given(taskService.findById(999L)).willThrow(ResourceNotFoundException.of("Task", 999L));

        mockMvc.perform(get("/api/v1/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/resource-not-found"))
                .andExpect(jsonPath("$.detail").value("Task not found with id: 999"))
                .andExpect(jsonPath("$.resource").value("Task"))
                .andExpect(jsonPath("$.identifier").value(999))
                .andExpect(jsonPath("$.instance").value("/api/v1/tasks/999"));
    }

    @Test
    void shouldBindAllFiltersAndPaginationIntoTheServiceCall() throws Exception {
        given(taskService.search(any(TaskFilter.class), any(Pageable.class)))
                .willReturn(PageResponse.from(new org.springframework.data.domain.PageImpl<>(
                        List.of(SAMPLE_TASK),
                        org.springframework.data.domain.PageRequest.of(1, 5),
                        11)));

        mockMvc.perform(get("/api/v1/tasks")
                        .param("status", "IN_PROGRESS")
                        .param("projectId", "1")
                        .param("assigneeId", "42")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "dueDate,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));

        ArgumentCaptor<TaskFilter> filterCaptor = ArgumentCaptor.forClass(TaskFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(taskService).should().search(filterCaptor.capture(), pageableCaptor.capture());

        assertThat(filterCaptor.getValue())
                .isEqualTo(new TaskFilter(TaskStatus.IN_PROGRESS, 1L, 42L, null));
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("dueDate")).isNotNull();
    }

    @Test
    void shouldApplyPaginationDefaultsWhenNoFilterIsSupplied() throws Exception {
        given(taskService.search(any(TaskFilter.class), any(Pageable.class)))
                .willReturn(PageResponse.from(new org.springframework.data.domain.PageImpl<>(List.of())));

        mockMvc.perform(get("/api/v1/tasks")).andExpect(status().isOk());

        ArgumentCaptor<TaskFilter> filterCaptor = ArgumentCaptor.forClass(TaskFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(taskService).should().search(filterCaptor.capture(), pageableCaptor.capture());

        assertThat(filterCaptor.getValue().isEmpty()).isTrue();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void shouldExplainWhichEnumValuesAreAcceptedOnAnInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("status", "FINISHED"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/bad-request"))
                .andExpect(jsonPath("$.parameter").value("status"))
                .andExpect(jsonPath("$.acceptedValues",
                        org.hamcrest.Matchers.contains("TODO", "IN_PROGRESS", "DONE")))
                .andExpect(jsonPath("$.detail",
                        org.hamcrest.Matchers.containsString("Accepted values: TODO, IN_PROGRESS, DONE")));
    }

    @Test
    void shouldPatchOnlyTheSuppliedFields() throws Exception {
        given(taskService.patch(eq(10L), any(TaskPatchDto.class))).willReturn(SAMPLE_TASK);

        mockMvc.perform(patch("/api/v1/tasks/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "IN_PROGRESS" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        ArgumentCaptor<TaskPatchDto> captor = ArgumentCaptor.forClass(TaskPatchDto.class);
        then(taskService).should().patch(eq(10L), captor.capture());

        TaskPatchDto patch = captor.getValue();
        assertThat(patch.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(patch.title()).isNull();
        assertThat(patch.priority()).isNull();
        assertThat(patch.assigneeId()).isNull();
    }

    @Test
    void shouldReturnNoContentOnDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/10"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(taskService).should().delete(10L);
    }

    @Test
    void shouldRejectANonPositiveIdentifier() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/validation-error"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void shouldSurfaceAnUnsupportedSortPropertyAsBadRequest() throws Exception {
        willThrow(new com.taskmanagement.exception.BadRequestException(
                "Cannot sort by 'assignee.password'. Sortable properties: id, title"))
                .given(taskService).search(any(TaskFilter.class), any(Pageable.class));

        mockMvc.perform(get("/api/v1/tasks").param("sort", "assignee.password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/bad-request"))
                .andExpect(jsonPath("$.detail",
                        org.hamcrest.Matchers.containsString("Cannot sort by 'assignee.password'")));
    }
}
