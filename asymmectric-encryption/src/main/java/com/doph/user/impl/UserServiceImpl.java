package com.doph.user.impl;

import com.doph.exception.BusinessException;
import com.doph.exception.ErrorCode;
import com.doph.user.User;
import com.doph.user.UserMapper;
import com.doph.user.UserRepository;
import com.doph.user.UserService;
import com.doph.user.request.ChangePasswordRequest;
import com.doph.user.request.ProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;


    @Override
    public void updateProfileInformation(ProfileUpdateRequest request, String userId) {
        final User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, userId));
        this.userMapper.mergerUserInfo(user, request);
        this.userRepository.save(user);
    }

    @Override
    public void changePassword(ChangePasswordRequest request, String userId) {
        if(!request.getNewPassword().equals(request.getConfirmNewPassword())){
            throw new BusinessException(ErrorCode.CHANGE_PASSWORD_MISMATCH);
        }
        final User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, userId));
        if(!this.passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())){
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        final String encoded = this.passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encoded);
        this.userRepository.save(user);
    }

    @Override
    public void deactivateAccount(String userId) {
        final User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, userId));
        if(!user.isEnabled()){
            throw new BusinessException(ErrorCode.THE_ACCOUNT_ALREADY_DEACTIVATED);
        }
        user.setEnabled(false);
        this.userRepository.save(user);
    }

    @Override
    public void reactivateAccount(String userId) {
        final User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, userId));
        if(user.isEnabled()){
            throw new BusinessException(ErrorCode.THE_ACCOUNT_ALREADY_ACTIVATED);
        }
        user.setEnabled(true);
        this.userRepository.save(user);
    }

    @Override
    public void deleteAccount(String userId) {
        // this method need the rest of the entities
        // the logic is just to schedule a profile for deletion
        // and a scheduled job will pick the profiles and delete everything
    }

    @Override
    public UserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCAse(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: "+email));
    }
}
