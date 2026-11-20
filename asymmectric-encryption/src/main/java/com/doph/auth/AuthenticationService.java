package com.doph.auth;

import com.doph.auth.request.AuthenticationRequest;
import com.doph.auth.request.RefreshRequest;
import com.doph.auth.request.RegistrationRequest;
import com.doph.auth.response.AuthenticationResponse;

public interface AuthenticationService {

    AuthenticationResponse login(AuthenticationRequest request);

    void register(RegistrationRequest request);

    AuthenticationResponse refreshToken(RefreshRequest request);

}
