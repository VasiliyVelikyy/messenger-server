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

/**
 * Сервис для управления пользователями и их контактами.
 * Хранит данные в памяти с использованием ConcurrentHashMap для потокобезопасности.
 */
@Service
@Slf4j
public class UserService {

    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    /**
     * Инициализирует тестовых пользователей при старте приложения.
     * Пароли хешируются с использованием BCrypt для безопасности.
     */
    @PostConstruct
    public void initTestUsers() {
        users.put("1", new User("1", BCrypt.hashpw("1", BCrypt.gensalt()), "Василий"));
        users.put("admin", new User("admin", BCrypt.hashpw("1", BCrypt.gensalt()), "Василий"));
        users.put("anna", new User("anna", BCrypt.hashpw("pass456", BCrypt.gensalt()), "Анна"));
        users.put("ivan", new User("ivan", BCrypt.hashpw("qwerty", BCrypt.gensalt()), "Иван"));
        users.put("maria", new User("maria", BCrypt.hashpw("demo789", BCrypt.gensalt()), "Мария"));
        log.info("Test users initialized: {} users loaded", users.size());
    }

    /**
     * Возвращает список контактов для указанного пользователя, исключая его самого.
     *
     * @param excludeLogin логин текущего пользователя, которого нужно исключить из списка
     * @return список объектов ContactDto с информацией о контактах
     */
    public List<ContactDto> getAllContacts(String excludeLogin) {
        return users.values().stream()
                .filter(u -> !u.getLogin().equals(excludeLogin))
                .map(u -> new ContactDto(u.getLogin(), u.getDisplayName(), u.isOnline()))
                .collect(Collectors.toList());
    }

    /**
     * Находит пользователя по логину.
     *
     * @param login логин для поиска
     * @return Optional с найденным пользователем или пустой, если пользователь не найден
     */
    public Optional<User> findByLogin(String login) {
        return Optional.ofNullable(users.get(login));
    }

    /**
     * Проверяет существование пользователя с указанным логином.
     *
     * @param login логин для проверки
     * @return true если пользователь существует, false иначе
     */
    public boolean existsByLogin(String login) {
        return users.containsKey(login);
    }

    /**
     * Устанавливает статус онлайн/офлайн для указанного пользователя.
     *
     * @param login  логин пользователя
     * @param online новый статус: true — онлайн, false — офлайн
     */
    public void setOnlineStatus(String login, boolean online) {
        User user = users.get(login);
        if (user != null) {
            user.setOnline(online);
            log.debug("User {} status set to {}", login, online ? "online" : "offline");
        } else {
            log.warn("Attempted to set status for non-existent user: {}", login);
        }
    }

    /**
     * Возвращает коллекцию всех пользователей системы.
     *
     * @return итерируемая коллекция объектов User
     */
    public Iterable<User> getAllUsers() {
        return users.values();
    }
}
