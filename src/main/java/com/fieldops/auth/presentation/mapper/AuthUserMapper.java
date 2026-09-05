package com.fieldops.auth.presentation.mapper;
import com.fieldops.auth.presentation.dto.AuthUserResponse;
import com.fieldops.user.domain.model.User;
import org.springframework.stereotype.Component;
@Component
public class AuthUserMapper {
    public AuthUserResponse toResponse(User user) {
        return new AuthUserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
