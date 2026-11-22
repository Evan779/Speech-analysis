package com.example.voiceapp.controller;

import com.example.voiceapp.model.AppUser;
import com.example.voiceapp.model.RegisterForm;
import com.example.voiceapp.repo.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult br) {
        if (users.existsByUsername(form.getUsername()))
            br.rejectValue("username", "dup", "That username is taken");
        if (users.existsByEmail(form.getEmail()))
            br.rejectValue("email", "dup", "That email is already registered");
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword()))
            br.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        if (br.hasErrors()) return "register";

        AppUser u = new AppUser();
        u.setUsername(form.getUsername());
        u.setEmail(form.getEmail().toLowerCase());
        u.setPasswordHash(encoder.encode(form.getPassword()));
        users.save(u);
        return "redirect:/login?registered";
    }
}
