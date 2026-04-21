package ru.moskalev.server.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import ru.moskalev.server.dto.ContactDto;
import ru.moskalev.server.model.User;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserService {
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    @PostConstruct
    public void initTestUsers() {
        users.put("admin",  new User("admin",  BCrypt.hashpw("123", BCrypt.gensalt()), "Василий"));
        users.put("anna",   new User("anna",   BCrypt.hashpw("pass456", BCrypt.gensalt()), "Анна"));
        users.put("ivan",   new User("ivan",   BCrypt.hashpw("qwerty", BCrypt.gensalt()), "Иван"));
        users.put("maria",  new User("maria",  BCrypt.hashpw("demo789", BCrypt.gensalt()), "Мария"));
        log.info("Test user download");
    }

    public List<ContactDto> getAllContacts(String excludeLogin) {
        return users.values().stream()
                .filter(u -> !u.getLogin().equals(excludeLogin))
                .map(u -> new ContactDto(u.getLogin(), u.getDisplayName(), u.isOnline()))
                .collect(Collectors.toList());
    }

    public Optional<User> findByLogin(String login) {
        return Optional.ofNullable(users.get(login));
    }

    public boolean existsByLogin(String login) {
        return users.containsKey(login);
    }

    public void setOnlineStatus(String login, boolean online) {
        User user = users.get(login);
        if (user != null) {
            user.setOnline(online);
        }
    }

    public Iterable<User> getAllUsers() {
        return users.values();
    }

}
