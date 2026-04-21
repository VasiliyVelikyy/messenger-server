package ru.moskalev.server.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.moskalev.server.dto.ChatMessage;
import ru.moskalev.server.dto.ContactDto;
import ru.moskalev.server.repository.MessageRepository;
import ru.moskalev.server.service.AuthService;
import ru.moskalev.server.service.UserService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик WebSocket-соединений для чата.
 * Обрабатывает авторизацию, отправку сообщений, историю и управление сообщениями.
 */
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final MessageRepository messageRepository;

    /**
     * Карта активных сессий: логин пользователя → его сессия.
     * Используется для отправки сообщений конкретному пользователю.
     */
    private final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> json = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) json.get("type");

        boolean isAuth = session.getAttributes().containsKey("userLogin");
        if (!isAuth && !"AUTH".equalsIgnoreCase(type)) {
            sendError(session, "NOT_AUTHENTICATED", "Сначала пройдите авторизацию");
            return;
        }

        switch (type.toUpperCase()) {
            case "AUTH" -> handleAuth(session, json);
            case "SEND_MESSAGE" -> handleSendMessage(session, json);
            case "PING" -> session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
            case "GET_CONTACTS" -> handleGetContacts(session);
            case "GET_HISTORY" -> handleGetHistory(session, json);
            case "EDIT_MESSAGE" -> handleEditMessage(session, json);
            case "DELETE_MESSAGE" -> handleDeleteMessage(session, json);
            default -> sendError(session, "UNKNOWN_TYPE", "Неизвестный тип: " + type);
        }
    }

    /**
     * Обрабатывает запрос авторизации пользователя.
     *
     * @param session текущая WebSocket-сессия
     * @param json    входящее сообщение с данными авторизации
     * @throws IOException если произошла ошибка при отправке ответа
     */
    private void handleAuth(WebSocketSession session, Map<String, Object> json) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) json.get("payload");

        AuthService.AuthResult result = authService.authenticate(
                payload.get("login"),
                payload.get("password")
        );

        if (result.success()) {
            String login = result.user().getLogin();
            session.getAttributes().put("userLogin", login);
            activeSessions.put(login, session);

            sendSuccess(session, "AUTH_SUCCESS", Map.of(
                    "displayName", result.user().getDisplayName()
            ));
        } else {
            sendError(session, "AUTH_FAILED", result.error());
            session.close();
        }
    }

    /**
     * Обрабатывает отправку сообщения от одного пользователя другому.
     *
     * @param session сессия отправителя
     * @param json    сообщение с параметрами: to, text
     * @throws Exception если произошла ошибка при обработке
     */
    private void handleSendMessage(WebSocketSession session, Map<String, Object> json) throws Exception {
        String fromLogin = (String) session.getAttributes().get("userLogin");
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) json.get("payload");

        String toLogin = payload.get("to");
        String text = payload.get("text");

        ChatMessage msg = new ChatMessage(fromLogin, toLogin, text, System.currentTimeMillis());
        messageRepository.saveMessage(msg);

        WebSocketSession targetSession = activeSessions.get(toLogin);
        if (targetSession != null && targetSession.isOpen()) {
            String jsonMsg = objectMapper.writeValueAsString(Map.of(
                    "type", "NEW_MESSAGE",
                    "payload", msg
            ));
            targetSession.sendMessage(new TextMessage(jsonMsg));
        }

        String ackJson = objectMapper.writeValueAsString(Map.of(
                "type", "MESSAGE_ACK",
                "payload", msg
        ));
        session.sendMessage(new TextMessage(ackJson));
    }

    /**
     * Возвращает историю переписки между текущим пользователем и указанным контактом.
     *
     * @param session сессия запросившего пользователя
     * @param json    сообщение с параметром targetLogin
     * @throws Exception если произошла ошибка при сериализации или отправке
     */
    private void handleGetHistory(WebSocketSession session, Map<String, Object> json) throws Exception {
        String myLogin = (String) session.getAttributes().get("userLogin");
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) json.get("payload");
        String targetLogin = payload.get("targetLogin");

        List<ChatMessage> history = messageRepository.getHistory(myLogin, targetLogin);

        String jsonHist = objectMapper.writeValueAsString(Map.of(
                "type", "HISTORY",
                "payload", Map.of("targetLogin", targetLogin, "messages", history)
        ));
        session.sendMessage(new TextMessage(jsonHist));
    }

    /**
     * Возвращает список контактов текущего пользователя.
     *
     * @param session авторизованная сессия пользователя
     * @throws Exception если произошла ошибка при получении контактов или отправке ответа
     */
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

    /**
     * Обрабатывает запрос на редактирование сообщения.
     *
     * @param session сессия пользователя, инициировавшего редактирование
     * @param json    сообщение с параметрами: to, originalText, newText
     * @throws Exception если произошла ошибка при обновлении или рассылке
     */
    private void handleEditMessage(WebSocketSession session, Map<String, Object> json) throws Exception {
        String fromLogin = (String) session.getAttributes().get("userLogin");
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) json.get("payload");

        String toLogin = payload.get("to");
        String originalText = payload.get("originalText");
        String newText = payload.get("newText");

        if (messageRepository.updateMessage(fromLogin, toLogin, originalText, newText)) {
            broadcastUpdate(toLogin, fromLogin, originalText, newText);
        } else {
            sendError(session, "EDIT_FAILED", "Не удалось изменить сообщение");
        }
    }

    /**
     * Обрабатывает запрос на удаление сообщения.
     *
     * @param session сессия пользователя, инициировавшего удаление
     * @param json    сообщение с параметрами: to, text
     * @throws Exception если произошла ошибка при удалении или рассылке
     */
    private void handleDeleteMessage(WebSocketSession session, Map<String, Object> json) throws Exception {
        String fromLogin = (String) session.getAttributes().get("userLogin");
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) json.get("payload");

        String toLogin = payload.get("to");
        String text = payload.get("text");

        if (messageRepository.deleteMessage(fromLogin, toLogin, text)) {
            broadcastDelete(toLogin, fromLogin, text);
        } else {
            sendError(session, "DELETE_FAILED", "Не удалось удалить сообщение");
        }
    }

    /**
     * Рассылает уведомление об обновлении сообщения обоим участникам диалога.
     *
     * @param user1        первый участник диалога
     * @param user2        второй участник диалога
     * @param originalText исходный текст сообщения
     * @param newText      новый текст сообщения
     * @throws Exception если произошла ошибка при сериализации или отправке
     */
    private void broadcastUpdate(String user1, String user2, String originalText, String newText) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "type", "MESSAGE_UPDATED",
                "payload", Map.of("originalText", originalText, "newText", newText)
        ));

        WebSocketSession s1 = activeSessions.get(user1);
        if (s1 != null && s1.isOpen()) {
            s1.sendMessage(new TextMessage(json));
        }
        WebSocketSession s2 = activeSessions.get(user2);
        if (s2 != null && s2.isOpen()) {
            s2.sendMessage(new TextMessage(json));
        }
    }

    /**
     * Рассылает уведомление об удалении сообщения обоим участникам диалога.
     *
     * @param user1 первый участник диалога
     * @param user2 второй участник диалога
     * @param text  текст удалённого сообщения
     * @throws Exception если произошла ошибка при сериализации или отправке
     */
    private void broadcastDelete(String user1, String user2, String text) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "type", "MESSAGE_DELETED",
                "payload", Map.of("text", text)
        ));

        WebSocketSession s1 = activeSessions.get(user1);
        if (s1 != null && s1.isOpen()) {
            s1.sendMessage(new TextMessage(json));
        }
        WebSocketSession s2 = activeSessions.get(user2);
        if (s2 != null && s2.isOpen()) {
            s2.sendMessage(new TextMessage(json));
        }
    }

    /**
     * Отправляет успешный ответ клиенту.
     *
     * @param session целевая сессия
     * @param type    тип ответа
     * @param payload данные ответа
     * @throws IOException если произошла ошибка при отправке
     */
    private void sendSuccess(WebSocketSession session, String type, Map<String, ?> payload) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of("type", type, "payload", payload)
        )));
    }

    /**
     * Отправляет ответ с ошибкой клиенту.
     *
     * @param session целевая сессия
     * @param code    код ошибки
     * @param msg     описание ошибки
     * @throws IOException если произошла ошибка при отправке
     */
    private void sendError(WebSocketSession session, String code, String msg) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of("type", "ERROR", "payload", Map.of("code", code, "message", msg))
        )));
    }

}