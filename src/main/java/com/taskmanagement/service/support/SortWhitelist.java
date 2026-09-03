package com.taskmanagement.service.support;

import com.taskmanagement.exception.BadRequestException;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards the {@code sort} query parameter against an explicit allow-list.
 *
 * <p>Design decision: {@code sort} is client-controlled and is fed straight into
 * a JPQL {@code ORDER BY}. Left unchecked it is both a robustness problem (an
 * unknown property throws {@code PropertyReferenceException} deep inside Spring
 * Data) and an information-disclosure one — {@code ?sort=assignee.password}
 * lets a caller order by, and therefore binary-search, a column that is never
 * exposed in any response. Validating against a whitelist of the fields the API
 * publicly documents closes both.
 */
public final class SortWhitelist {

    private SortWhitelist() {
    }

    /**
     * @throws BadRequestException if the pageable sorts by any property outside
     *                             {@code allowedProperties}; the message names
     *                             the accepted values so the caller can recover.
     */
    public static void validate(Pageable pageable, Set<String> allowedProperties) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return;
        }
        pageable.getSort().forEach(order -> {
            if (!allowedProperties.contains(order.getProperty())) {
                // Sorted for a stable message: Set.of has a randomized iteration
                // order per JVM run, which would otherwise make the error text
                // (and any test or documentation quoting it) non-deterministic.
                String accepted = allowedProperties.stream().sorted().collect(Collectors.joining(", "));
                throw new BadRequestException(
                        "Cannot sort by '%s'. Sortable properties: %s".formatted(order.getProperty(), accepted));
            }
        });
    }
}
