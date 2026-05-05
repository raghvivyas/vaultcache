package com.vaultcache.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.validation.constraints.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank @Size(min=3,max=50) private String username;
    @NotBlank @Size(min=6,max=100) private String password;
}
