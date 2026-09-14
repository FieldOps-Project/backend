package com.fieldops.user.presentation.mapper;

import com.fieldops.user.domain.model.User;
import com.fieldops.user.presentation.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getPhone(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion()
        );
    }
}
