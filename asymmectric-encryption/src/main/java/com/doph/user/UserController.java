package com.doph.user;

import com.doph.user.request.ChangePasswordRequest;
import com.doph.user.request.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User API")
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void updateProfileInfo(
            @RequestBody @Valid final ProfileUpdateRequest request,
           final Authentication authentication
            ){
        this.userService.updateProfileInformation(request, getUserId(authentication));
    }

    @PostMapping("/me/password")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void changePassword(
            @RequestBody @Valid ChangePasswordRequest request,
            final Authentication authentication
            ){
        this.userService.changePassword(request, getUserId(authentication));
    }

    @PatchMapping("/me/deactivate")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void deactivateAccount(final Authentication authentication){
        this.userService.deactivateAccount(getUserId(authentication));
    }

    @PatchMapping("/me/activate")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void reactivateAccount(final Authentication authentication){
        this.userService.reactivateAccount(getUserId(authentication));
    }

    @DeleteMapping("/me")
    public void deleteAccount(final Authentication authentication){
        this.userService.deleteAccount(getUserId(authentication));
    }

    private String getUserId(final Authentication authentication){
        return ((User) authentication.getPrincipal()).getId();
    }
}
