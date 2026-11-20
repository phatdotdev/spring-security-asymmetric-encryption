package com.doph.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegistrationRequest {
    @NotBlank(message = "VALIDATION.REGISTRATION.FIRSTNAME.NOT_BLANK")
    @Size(
            min = 5,
            max = 50,
            message = "VALIDATION.REGISTRATION.FIRSTNAME.SIZE"
    )
    @Pattern(
            regexp = "^[\\p{L} '-]+$]",
            message = "VALIDATION.REGISTRATION.FIRSTNAME.PATTERN"
    )
    @Schema(example = "Do")
    private String firstName;
    @NotBlank(message = "VALIDATION.REGISTRATION.LASTNAME.NOT_BLANK")
    @Size(
            min = 5,
            max = 50,
            message = "VALIDATION.REGISTRATION.LASTTNAME.SIZE"
    )
    @Pattern(
            regexp = "^[\\p{L} '-]+$]",
            message = "VALIDATION.REGISTRATION.LASTNAME.PATTERN"
    )
    @Schema(example = "Phat")
    private String lastName;

    @NotBlank(message = "VALIDATION.REGISTRATION.EMAIL.NOT_BLANK")
    @Email(message = "VALIDATION.REGISTRATION.EMAIL.INVALID")
    @Schema(example = "doph@gmail.com")
    private String email;

    @NotBlank(message = "VALIDATION.REGISTRATION.PHONE.NOT_BLANK")
    @Pattern(
            regexp = "^\\+[0-9]{10,13}",
            message = "VALID.REGISTRATION.PHONE.FORMAT"
    )
    @Schema(example = "+84987654321")
    private String phoneNumber;

    @NotBlank(message = "VALIDATION.REGISTRATION.PASSWORD.NOT_BLANK")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*\\W).*$"
    )
    @Size(
            min = 5,
            max = 50,
            message = "VALIDATION.REGISTRATION.PASSWORD.SIZE"
    )
    @Schema(
            example = "<password>"
    )
    private String password;

    @NotBlank(message = "VALIDATION.REGISTRATION.CONFIRM_PASSWORD.NOT_BLANK")
    @Size(
            min = 5,
            max = 50,
            message = "VALIDATION.REGISTRATION.CONFIRM_PASSWORD.SIZE"
    )
    @Schema(
            example = "<password>"
    )
    private String confirmPassword;

}
