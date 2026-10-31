package com.wayrapass.service;

import com.wayrapass.model.User;

public interface PasswordRecoveryMailService {

    void sendPasswordReset(User user, String rawToken);
}
