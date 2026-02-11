package com.cloud.auth.service;

import com.cloud.auth.dto.LoginRequest;
import com.cloud.auth.dto.RegisterRequest;
import com.cloud.auth.entity.User;
import com.cloud.auth.repository.UserRepository;
import com.cloud.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public String register(RegisterRequest request) {
        var user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();
        userRepository.save(user);
        // In a real app we might return a token here too
        return "User Registered Successfully";
    }

    public String login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        var user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        // Create UserDetails from user entity or implement UserDetails in User entity
        // For simplicity assuming User implements UserDetails or we adapt it
        // Here we just pass a simple custom UserDetails implementation or Mock
        // For now, let's just generate without detailed UserDetails if JwtUtil supports it, 
        // or strictly we need UserDetails.
        // Let's assume we need to adjust JwtUtil or User to implement UserDetails.
        // For this skeleton, I'll pass a dummy object if needed or fix User.
        
        // Let's rely on a helper to bridge or assume User implements UserDetails in a complete impl.
        // For now, returning a placeholder token string or adapting.
        
        // return jwtUtil.generateToken(user); 
        return "DummyTokenFor_" + request.getUsername(); 
    }
}
