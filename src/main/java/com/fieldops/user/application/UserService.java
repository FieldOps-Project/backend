package com.fieldops.user.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.presentation.dto.PaginationDefaults;
import com.fieldops.user.domain.exception.EmailAlreadyExistsException;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import com.fieldops.user.infrastructure.persistence.UserSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "email", "role", "status", "createdAt", "updatedAt"
    );
    private static final char[] TEMPORARY_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventService auditEventService;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, null);
    }

    @Autowired
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditEventService auditEventService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventService = auditEventService;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    public User createUser(String name, String email, String rawPassword, UserRole role, UserStatus status, String phone) {
        User user = User.create(
                name,
                normalizeEmail(email),
                passwordEncoder.encode(rawPassword),
                role,
                status,
                phone
        );

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (!isEmailUniqueViolation(ex)) {
                throw ex;
            }
            throw new EmailAlreadyExistsException();
        }

        recordAudit("USER_CREATED", saved, null, auditState(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.USERS_READ)
    public User findById(UUID id) {
        return findRequired(id);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.USERS_READ)
    public Page<User> findAll(
            String name,
            String email,
            UserRole role,
            UserStatus status,
            int page,
            int size,
            String sort
    ) {
        Pageable pageable = PaginationDefaults.of(
                page,
                size,
                parseSort(sort)
        );
        return userRepository.findAll(UserSpecifications.withFilters(name, email, role, status), pageable);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    public User updateUser(UUID id, String name, String email, UserRole role, String phone) {
        User user = findRequired(id);
        String normalizedEmail = normalizeEmail(email);
        ensureEmailAvailable(id, normalizedEmail);

        String before = auditState(user);
        UserRole previousRole = user.getRole();
        user.updateProfile(name, normalizedEmail, role, phone);

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (!isEmailUniqueViolation(ex)) {
                throw ex;
            }
            throw new EmailAlreadyExistsException();
        }

        recordAudit("USER_UPDATED", saved, before, auditState(saved));
        if (previousRole != role) {
            recordAudit("USER_ROLE_CHANGED", saved, before, auditState(saved));
        }
        return saved;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    public User updateStatus(UUID id, UserStatus status) {
        User user = findRequired(id);
        UserStatus previousStatus = user.getStatus();
        String before = auditState(user);
        user.updateStatus(status);
        User saved = userRepository.save(user);
        if (previousStatus != saved.getStatus()) {
            recordAudit("USER_STATUS_CHANGED", saved, before, auditState(saved));
        }
        return saved;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    public User inactivate(UUID id) {
        return updateStatus(id, UserStatus.INACTIVE);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    public User changePassword(UUID id, String rawPassword) {
        User user = findRequired(id);
        user.changePassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.USERS_MANAGE)
    public String resetPassword(UUID id) {
        User user = findRequired(id);
        String temporaryPassword = generateTemporaryPassword();
        user.changePassword(passwordEncoder.encode(temporaryPassword));
        User saved = userRepository.save(user);
        recordAudit("USER_PASSWORD_RESET", saved, null, null);
        return temporaryPassword;
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

    private void ensureEmailAvailable(UUID userId, String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !userId.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new EmailAlreadyExistsException();
                });
    }

    private Sort parseSort(String sort) {
        String requestedSort = sort == null || sort.isBlank() ? "createdAt,desc" : sort.trim();
        String[] parts = requestedSort.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank() || !ALLOWED_SORT_FIELDS.contains(parts[0].trim())) {
            throw invalidSort();
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException ex) {
                throw invalidSort();
            }
        }
        return Sort.by(direction, parts[0].trim());
    }

    private InvalidRequestException invalidSort() {
        return new InvalidRequestException(
                "INVALID_SORT",
                "Sort must use an allowed user field and direction asc or desc"
        );
    }

    private String generateTemporaryPassword() {
        char[] password = new char[20];
        for (int index = 0; index < password.length; index++) {
            password[index] = TEMPORARY_PASSWORD_ALPHABET[
                    secureRandom.nextInt(TEMPORARY_PASSWORD_ALPHABET.length)
            ];
        }
        return new String(password);
    }

    private String auditState(User user) {
        return "name=" + user.getName()
                + ";email=" + user.getEmail()
                + ";role=" + user.getRole()
                + ";status=" + user.getStatus()
                + ";phone=" + user.getPhone();
    }

    private void recordAudit(String action, User user, String before, String after) {
        if (auditEventService != null) {
            auditEventService.recordUserEvent(action, user.getId(), before, after);
        }
    }
}
