package com.usm.workorder.dto;

import jakarta.validation.constraints.NotBlank;

/** US-09/16, FR-08, BR-07 (guide §4.2) - assigned technician only, appends to the action log. */
public record ProgressUpdateRequest(
        @NotBlank(message = "note is required")
        String note
) {
}
