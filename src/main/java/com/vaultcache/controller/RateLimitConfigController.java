package com.vaultcache.controller;

import com.vaultcache.model.*;
import com.vaultcache.service.RateLimitConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class RateLimitConfigController {

    private final RateLimitConfigService configService;

    /** POST /api/configs — Create a new rate limit config */
    @PostMapping
    public ResponseEntity<ApiResponse<RateLimitConfigResponse>> create(
            @Valid @RequestBody RateLimitConfigRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Config created", configService.create(req)));
    }

    /** GET /api/configs — List all configs */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RateLimitConfigResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(configService.listAll()));
    }

    /** GET /api/configs/{id} — Get a specific config */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RateLimitConfigResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(configService.getById(id)));
    }

    /** PUT /api/configs/{id} — Update an existing config */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RateLimitConfigResponse>> update(
            @PathVariable Long id, @Valid @RequestBody RateLimitConfigRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Config updated", configService.update(id, req)));
    }

    /** POST /api/configs/{id}/block — Block a subject (all requests rejected) */
    @PostMapping("/{id}/block")
    public ResponseEntity<ApiResponse<Void>> block(@PathVariable Long id) {
        configService.block(id, true);
        return ResponseEntity.ok(ApiResponse.ok("Subject blocked", null));
    }

    /** POST /api/configs/{id}/unblock — Unblock a subject */
    @PostMapping("/{id}/unblock")
    public ResponseEntity<ApiResponse<Void>> unblock(@PathVariable Long id) {
        configService.block(id, false);
        return ResponseEntity.ok(ApiResponse.ok("Subject unblocked", null));
    }

    /** DELETE /api/configs/{id} — Remove a config and invalidate Redis keys */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        configService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Config deleted", null));
    }

    /** GET /api/configs/stats/{subject} — Usage stats for a subject */
    @GetMapping("/stats/{subject}")
    public ResponseEntity<ApiResponse<UsageStatsResponse>> stats(@PathVariable String subject) {
        return ResponseEntity.ok(ApiResponse.ok(configService.getStats(subject)));
    }
}
