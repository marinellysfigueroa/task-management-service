package com.taskmanagement.service;

import com.taskmanagement.dto.ProjectRequestDto;
import com.taskmanagement.dto.ProjectResponseDto;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.model.Project;
import com.taskmanagement.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectResponseDto createProject(ProjectRequestDto request) {
        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(request.getOwnerId())
                .build();

        return toResponseDto(projectRepository.save(project));
    }

    public ProjectResponseDto getProjectById(Long id) {
        return toResponseDto(findProjectOrThrow(id));
    }

    public List<ProjectResponseDto> getAllProjects() {
        return projectRepository.findAll().stream().map(this::toResponseDto).toList();
    }

    public List<ProjectResponseDto> getProjectsByOwner(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId).stream().map(this::toResponseDto).toList();
    }

    @Transactional
    public ProjectResponseDto updateProject(Long id, ProjectRequestDto request) {
        Project project = findProjectOrThrow(id);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setOwnerId(request.getOwnerId());
        return toResponseDto(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Project", id);
        }
        projectRepository.deleteById(id);
    }

    protected Project findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", id));
    }

    private ProjectResponseDto toResponseDto(Project project) {
        return ProjectResponseDto.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .ownerId(project.getOwnerId())
                .createdAt(project.getCreatedAt())
                .build();
    }
}
