package com.wayrapass.service;

import com.wayrapass.dto.request.UpdateUserProfileRequest;
import com.wayrapass.dto.response.UserResponse;
import com.wayrapass.model.Institution;
import com.wayrapass.model.Role;
import com.wayrapass.model.Student;
import com.wayrapass.model.User;
import com.wayrapass.repository.StudentRepository;
import com.wayrapass.repository.UserRepository;
import com.wayrapass.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final CurrentUser current;
    private final UserRepository users;
    private final StudentRepository students;

    @Transactional(readOnly = true)
    public UserResponse me() {
        return out(current.require());
    }

    @Transactional
    public UserResponse update(UpdateUserProfileRequest request) {
        User user = current.require();
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        return out(users.save(user));
    }

    private UserResponse out(User user) {
        Institution institution = user.getRole() == Role.STUDENT
                ? students.findByUserId(user.getId()).map(Student::getInstitution).orElse(null)
                : null;
        Long institutionId = institution == null ? null : institution.getId();
        UserResponse.InstitutionSummary institutionSummary = institution == null ? null
                : new UserResponse.InstitutionSummary(institution.getId(), displayName(institution));
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getRole(),
                user.isEnabled(), user.getCreatedAt(), user.getUpdatedAt(), institutionId, institutionSummary);
    }

    private static String displayName(Institution institution) {
        return institution.getCampusName() == null || institution.getCampusName().isBlank()
                ? institution.getName()
                : institution.getName() + " — " + institution.getCampusName();
    }
}
