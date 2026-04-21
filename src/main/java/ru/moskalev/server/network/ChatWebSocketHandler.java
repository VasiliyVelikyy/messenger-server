package ru.moskalev.server.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.moskalev.server.dto.ContactDto;
import ru.moskalev.server.service.AuthService;
import ru.moskalev.server.service.UserService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    // Активные сессии: login → session (для отправки сообщений)
    private final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(AuthService authService, ObjectMapper objectMapper, UserService userService) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println("🔁 [ECHO] Получено: " + message.getPayload());
        Map<String, Object> json = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) json.get("type");

        // 1. Разрешаем только AUTH для неавторизованных сессий
        boolean isAuth = session.getAttributes().containsKey("userLogin");
        if (!isAuth && !"AUTH".equalsIgnoreCase(type)) {
            sendError(session, "NOT_AUTHENTICATED", "Сначала пройдите авторизацию");
            return;
        }

        // 2. Маршрутизация по типу
        switch (type.toUpperCase()) {
            case "AUTH" -> handleAuth(session, json);
            case "MESSAGE" -> handleSendMessage(session, json);
            case "PING" -> session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
            case "GET_CONTACTS" -> handleGetContacts(session);
            default -> sendError(session, "UNKNOWN_TYPE", "Неизвестный тип: " + type);
        }
    }

    private void handleAuth(WebSocketSession session, Map<String, Object> json) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) json.get("payload");

        AuthService.AuthResult result = authService.authenticate(
                payload.get("login"),
                payload.get("password")
        );

        if (result.success()) {
            // Переводим сессию в авторизованное состояние
            String login = result.user().getLogin();
            session.getAttributes().put("userLogin", login);
            activeSessions.put(login, session);

            sendSuccess(session, "AUTH_SUCCESS", Map.of(
                    "displayName", result.user().getDisplayName()
            ));
        } else {
            sendError(session, "AUTH_FAILED", result.error());
            session.close(); // Закрываем при ошибке входа
        }
    }

    private void handleSendMessage(WebSocketSession session, Map<String, Object> json) throws IOException {
        String fromLogin = (String) session.getAttributes().get("userLogin");

        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) json.get("payload");
        String toLogin = payload.get("to");
        String text = payload.get("text");

        // Простейшая маршрутизация
        WebSocketSession targetSession = activeSessions.get(toLogin);
        if (targetSession != null && targetSession.isOpen()) {
            var msgPayload = Map.of(
                    "from", fromLogin,
                    "text", text,
                    "timestamp", System.currentTimeMillis()
            );
            sendSuccess(targetSession, "NEW_MESSAGE", msgPayload);
            sendSuccess(session, "DELIVERED", Map.of("to", toLogin));
        } else {
            sendError(session, "USER_OFFLINE", "Пользователь не в сети");
        }
    }

    private void handleGetContacts(WebSocketSession session) throws Exception {
        String login = (String) session.getAttributes().get("userLogin");
        if (login == null) {
            sendError(session, "NOT_AUTHENTICATED", "Сначала войдите в систему");
            return;
        }

        List<ContactDto> contacts = userService.getAllContacts(login);
        Map<String, Object> response = Map.of(
                "type", "CONTACTS_LIST",
                "payload", Map.of("contacts", contacts)
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String login = (String) session.getAttributes().get("userLogin");
        if (login != null) {
            activeSessions.remove(login);
            authService.logout(login);
        }
    }

    // === Утилиты отправки JSON ===
    private void sendSuccess(WebSocketSession s, String type, Map<String, ?> payload) throws IOException {
        s.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of("type", type, "payload", payload)
        )));
    }

    private void sendError(WebSocketSession s, String code, String msg) throws IOException {
        s.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of("type", "ERROR", "payload", Map.of("code", code, "message", msg))
        )));
    }
}