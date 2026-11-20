package com.doph.user;

import ch.qos.logback.core.util.StringUtil;
import com.doph.auth.request.RegistrationRequest;
import com.doph.role.Role;
import com.doph.user.request.ProfileUpdateRequest;
import com.doph.user.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserMapper {

    private final PasswordEncoder passwordEncoder;

    public void mergerUserInfo(final User user, final ProfileUpdateRequest request){
        if(StringUtils.isNotBlank(request.getFirstName())
                && !user.getFirstName().equals(request.getFirstName())
        ){
            user.setFirstName(request.getFirstName());
        }
        if(StringUtils.isNotBlank(request.getLastName())
                && !user.getLastName().equals(request.getLastName())
        ){
            user.setLastName(request.getLastName());
        }
    }

    public User toUser(RegistrationRequest request) {
        return User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(this.passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .locked(false)
                .credentialsExpired(false)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
    }

    public UserResponse toUserResponse(final User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .dateOfBirth(user.getDateOfBirth())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .roles(user.getRoles() != null
                        ? user.getRoles().stream().map(Role::getName).toList()
                        : List.of())
                .build();
    }
}
