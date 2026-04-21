package ru.moskalev.server.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String login;
    private String passwordHash;
    private String displayName;
    private boolean online;

    public User(String login, String passwordHash, String displayName) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.online = false;
    }
}
