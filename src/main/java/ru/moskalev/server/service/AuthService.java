package ru.moskalev.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import ru.moskalev.server.model.User;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;

    /**
     * Основная логика проверки логина/пароля
     *
     * @param login введённый логин
     * @param password введённый пароль
     * @return результат аутентификации
     */
    public AuthResult authenticate(String login, String password) {
        if (login == null || login.isBlank()) {
            log.warn("Empty login");
            return new AuthResult(false, "LOGIN_EMPTY", null);
        }
        if (password == null || password.isBlank()) {
            log.warn("Empty password in user {}", login);
            return new AuthResult(false, "PASSWORD_EMPTY", null);
        }

        Optional<User> userOpt = userService.findByLogin(login);
        if (userOpt.isEmpty()) {
            log.warn("User '{}' not found", login);
            return new AuthResult(false, "USER_NOT_FOUND", null);
        }

        User user = userOpt.get();

        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            log.warn("Wrong password for user: {}", login);
            return new AuthResult(false, "INVALID_PASSWORD", null);
        }


        log.info("User '{}' success login", login);
        userService.setOnlineStatus(login, true);
        return new AuthResult(true, null, user);
    }

    public void logout(String login) {
        if (login != null) {
            userService.setOnlineStatus(login, false);
            log.info("User '{}' go out from system", login);
        }
    }

    public record AuthResult(
            boolean success,
            String error,
            User user
    ) {}
}