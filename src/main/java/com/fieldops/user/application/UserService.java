package com.fieldops.user.application;

import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.user.domain.exception.EmailAlreadyExistsException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(String name, String email, String rawPassword, UserRole role, UserStatus status, String phone) {
        User user = User.create(
                name,
                normalizeEmail(email),
                passwordEncoder.encode(rawPassword),
                role,
                status,
                phone
        );

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (!isEmailUniqueViolation(ex)) {
                throw ex;
            }
            throw new EmailAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return findRequired(id);
    }

    @Transactional
    public User updateStatus(UUID id, UserStatus status) {
        User user = findRequired(id);
        user.updateStatus(status);
        return userRepository.save(user);
    }

    @Transactional
    public User inactivate(UUID id) {
        return updateStatus(id, UserStatus.INACTIVE);
    }

    @Transactional
    public User changePassword(UUID id, String rawPassword) {
        User user = findRequired(id);
        user.changePassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    private User findRequired(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isEmailUniqueViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains("ux_users_email");
    }
}
