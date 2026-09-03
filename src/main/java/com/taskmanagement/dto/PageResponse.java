package com.taskmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Transport representation of a page of results.
 *
 * <p>Design decision: we deliberately do <em>not</em> serialize Spring Data's
 * {@link Page}/{@code PageImpl} straight to the wire. Its JSON shape is an
 * implementation detail that has already changed between Spring Data releases
 * (and logs a warning when serialized directly), and it leaks internals such as
 * the full {@code pageable}/{@code sort} objects. Owning a small, explicit
 * envelope keeps the public API contract stable and documentable.
 *
 * @param <T> element type, always a response DTO — never a JPA entity
 */
@Schema(name = "PageResponse", description = "A page of results together with its pagination metadata")
public record PageResponse<T>(

        @Schema(description = "Items contained in the current page")
        List<T> content,

        @Schema(description = "Zero-based index of the current page", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total number of elements matching the query across all pages", example = "137")
        long totalElements,

        @Schema(description = "Total number of available pages", example = "7")
        int totalPages,

        @Schema(description = "Whether this is the first page", example = "true")
        boolean first,

        @Schema(description = "Whether this is the last page", example = "false")
        boolean last
) {

    /**
     * Adapts a Spring Data page into the public envelope. Kept as a factory so
     * that callers never need to know the internal field ordering.
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
