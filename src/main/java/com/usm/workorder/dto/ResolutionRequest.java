package com.usm.workorder.dto;

import jakarta.validation.constraints.NotBlank;

/** US-10/17, FR-09, API-05/06, BR-08 (guide §4.2) - resolution text is mandatory. */
public record ResolutionRequest(
        @NotBlank(message = "resolution is required")
        String resolution
) {
}
