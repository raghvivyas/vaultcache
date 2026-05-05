package com.vaultcache.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private String username;
    private String role;
    public JwtResponse(String token, String username, String role) {
        this.token = token; this.username = username; this.role = role;
    }
}
