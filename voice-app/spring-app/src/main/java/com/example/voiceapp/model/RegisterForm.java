package com.example.voiceapp.model;

import jakarta.validation.constraints.*;

public class RegisterForm {
    @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "Letters, digits, _ . - only")
    private String username;
    @NotBlank @Email @Size(max = 255)
    private String email;
    @NotBlank @Size(min = 8, max = 72, message = "Use 8 to 72 characters")
    private String password;
    @NotBlank
    private String confirmPassword;

    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String v) { this.confirmPassword = v; }
}
