package com.cloud.auth.mapper;

import com.cloud.auth.dto.RegisterRequest;
import com.cloud.auth.dto.UserResponse; // Assuming you might have a response DTO, if not I'll just map to RegisterRequest for symmetry or handle appropriately
import com.cloud.auth.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(RegisterRequest request) {
        if (request == null) {
            return null;
        }
        return User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword()) // Note: Password encoding should ideally happen in service before
                                                 // saving
                .build();
    }

    // Example method if needed
    // public UserResponse toDto(User user) { ... }
}
