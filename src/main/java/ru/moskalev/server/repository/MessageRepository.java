package ru.moskalev.server.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.moskalev.server.dto.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static ru.moskalev.server.repository.MockHistoryPhrases.PHRASES_FROM;
import static ru.moskalev.server.repository.MockHistoryPhrases.PHRASES_TO;

/**
 * Сервис для хранения и управления историей сообщений чата.
 * Использует ConcurrentHashMap для потокобезопасного доступа к данным.
 */
@Service
@Slf4j
public class MessageRepository {
    private final ConcurrentHashMap<String, List<ChatMessage>> history = new ConcurrentHashMap<>();

    /**
     * Инициализирует тестовые сообщения при старте приложения.
     * Создаёт диалоги между тестовыми пользователями для демонстрации.
     */
    @PostConstruct
    public void initTestMessages() {
        log.info("Initializing test message history...");

        long baseTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2);

        createTestDialog("1", "anna", baseTime);
        createTestDialog("1", "ivan", baseTime + 300_000);
        createTestDialog("anna", "ivan", baseTime + 600_000);
        createTestDialog("admin", "maria", baseTime + 900_000);
        createTestDialog("1", "maria", baseTime + 1_200_000);

        log.info("Test messages loaded successfully");
    }

    /**
     * Создаёт тестовый диалог из 5 сообщений между двумя пользователями.
     *
     * @param user1 первый участник диалога
     * @param user2 второй участник диалога
     * @param startTime временная метка первого сообщения в диалоге
     */
    private void createTestDialog(String user1, String user2, long startTime) {
        for (int i = 0; i < 5; i++) {
            String from = (i % 2 == 0) ? user1 : user2;
            String to = (i % 2 == 0) ? user2 : user1;
            String text = (i % 2 == 0) ? PHRASES_FROM[i] : PHRASES_TO[i];
            long timestamp = startTime + i * 30_000;

            ChatMessage msg = new ChatMessage(from, to, text, timestamp);
            saveMessage(msg);
        }
        log.debug("Created test dialog: {} ↔ {}", user1, user2);
    }

    /**
     * Генерирует уникальный ключ для хранения истории диалога между двумя пользователями.
     * Ключ не зависит от порядка пользователей (user1_user2 == user2_user1).
     *
     * @param user1 первый пользователь
     * @param user2 второй пользователь
     * @return строковый ключ в формате "login1_login2" (в лексикографическом порядке)
     */
    private String getChatKey(String user1, String user2) {
        return user1.compareTo(user2) < 0 ? user1 + "_" + user2 : user2 + "_" + user1;
    }

    /**
     * Сохраняет новое сообщение в историю переписки.
     *
     * @param message объект сообщения для сохранения
     */
    public void saveMessage(ChatMessage message) {
        String key = getChatKey(message.from(), message.to());
        history.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(message);
    }

    /**
     * Возвращает историю переписки между двумя пользователями.
     *
     * @param user1 первый участник диалога
     * @param user2 второй участник диалога
     * @return список сообщений в хронологическом порядке, или пустой список если истории нет
     */
    public List<ChatMessage> getHistory(String user1, String user2) {
        String key = getChatKey(user1, user2);
        return history.getOrDefault(key, Collections.emptyList());
    }

    /**
     * Обновляет текст существующего сообщения.
     * Поиск осуществляется по отправителю и оригинальному тексту сообщения.
     *
     * @param from логин отправителя сообщения
     * @param to логин получателя сообщения
     * @param originalText исходный текст сообщения
     * @param newText новый текст сообщения
     * @return true если сообщение найдено и обновлено, false если не найдено
     */
    public boolean updateMessage(String from, String to, String originalText, String newText) {
        String key = getChatKey(from, to);
        List<ChatMessage> messages = history.get(key);

        if (messages == null) {
            return false;
        }

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg.from().equals(from) && msg.text().equals(originalText)) {
                ChatMessage updated = new ChatMessage(from, to, newText, msg.timestamp());
                messages.set(i, updated);
                log.debug("Message updated: {} → {}", from, to);
                return true;
            }
        }
        log.warn("Message not found for update: from={}, originalText={}", from, originalText);
        return false;
    }

    /**
     * Удаляет сообщение из истории переписки.
     * Поиск осуществляется по отправителю и тексту сообщения.
     *
     * @param from логин отправителя сообщения
     * @param to логин получателя сообщения
     * @param text текст сообщения для удаления
     * @return true если сообщение найдено и удалено, false если не найдено
     */
    public boolean deleteMessage(String from, String to, String text) {
        String key = getChatKey(from, to);
        List<ChatMessage> messages = history.get(key);

        if (messages == null) {
            return false;
        }

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg.from().equals(from) && msg.text().equals(text)) {
                messages.remove(i);
                log.debug("Message deleted: {} → {}", from, to);
                return true;
            }
        }
        log.warn("Message not found for deletion: from={}, text={}", from, text);
        return false;
    }
}
