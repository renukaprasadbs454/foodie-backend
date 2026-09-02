package com.foodie.debug.controller;

import com.foodie.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/debug")
@Tag(name = "Debug Utilities")
public class DebugController {

    private final JdbcTemplate jdbcTemplate;

    public DebugController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/cleanup-dummy")
    @Operation(summary = "Cleanup dummy restaurants")
    public ResponseEntity<ApiResponse<String>> cleanupDummy() {
        int updated = jdbcTemplate.update(
                "UPDATE restaurant SET status = 'REJECTED', rejection_reason = 'Dummy restaurant removed' WHERE LOWER(name) NOT LIKE '%royal%hotel%'");
        return ResponseEntity.ok(ApiResponse.success("Cleaned up " + updated + " dummy restaurants."));
    }
}
