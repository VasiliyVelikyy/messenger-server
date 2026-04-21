# Серверная часть задания

## Описание задания

1. Ваш проект должен состоять из двух частей - сервер и клиент (оба на языке Java)
   Все клиенты при запуске должны подключаться к единому серверу
2. По внешнему виду клиент должен как можно точнее повторять Telegram (темную тему)
3. Логика обмена сообщениями тоже должна быть реализована максимально похоже, но в
   объеме MVP
4. Не требуется шифрование, изменение или удаление уже отправленных сообщений,
   оповещение о прочтении, сохранение сообщений на сервере, передача чего-либо помимо
   текста, создание групповых чатов и т.д.
5. Авторизация предполагает ввод имени и пароля в окошке входа при каждом запуске
   приложения
6. Функция регистрации не обязательна, достаточно списка предопределенных тестовых
   пользователей
7. Для реализации графического интерфейса используйте библиотеки Java AWT/Swing и
   https://www.formdev.com/flatlaf
8. Сервер должен обеспечивать весь необходимый функционал передачи сообщений
   между несколькими клиентами и авторизации пользователей

## Telegram Chat Server

Серверная часть мессенджера на Spring Boot с поддержкой WebSocket для обмена сообщениями в реальном времени.

## Требования

| Компонент   | Версия      |
|-------------|-------------|
| Java        | 17 или выше |
| Maven       | 3.8+        |
| Spring Boot | 2.7+        |

## Конфигурация

### Порты и адреса

| Параметр             | Значение | Описание                           |
|----------------------|----------|------------------------------------|
| `server.port`        | `8080`   | HTTP-порт для WebSocket-соединений |
| `websocket.endpoint` | `/ws`    | Endpoint для WebSocket-подключений |
| `logging.level`      | `INFO`   | Уровень логирования по умолчанию   |

### Файл конфигурации

`src/main/resources/application.properties`:

```properties
server.port=8080
logging.level.ru.moskalev=INFO
logging.pattern.console=%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

## Запуск сервера

### Через Maven

```bash
# Сборка проекта
mvn clean package

# Запуск
mvn spring-boot:run
```

### Через JAR-файл

```bash
# Сборка исполняемого JAR
mvn clean package

# Запуск
java -jar target/messenger-server.jar
```

### Через IDE

1. Откройте проект в IntelliJ IDEA / Eclipse
2. Найдите класс с `@SpringBootApplication`
3. Запустите метод `main()`

## Подключение клиента

После запуска сервера подключите клиентское приложение:

```
WebSocket URL: ws://localhost:8080/ws
```

### Пример подключения через JavaScript

```javascript
const ws = new WebSocket("ws://localhost:8080/ws");

ws.onopen = () => {
    console.log("Connected to server");
    ws.send(JSON.stringify({
        type: "AUTH",
        payload: {login: "1", password: "1"}
    }));
};
```

### Тестовые учётные данные

| Логин   | Пароль    | Имя     |
|---------|-----------|---------|
| `1`     | `1`       | Василий |
| `admin` | `admin`   | Василий |
| `anna`  | `pass456` | Анна    |
| `ivan`  | `qwerty`  | Иван    |
| `maria` | `demo789` | Мария   |

## Протокол обмена сообщениями

Все сообщения передаются в формате JSON с полями `type` и `payload`.

### Авторизация

**Запрос:**

```json
{
  "type": "AUTH",
  "payload": {
    "login": "1",
    "password": "1"
  }
}
```

**Успешный ответ:**

```json
{
  "type": "AUTH_SUCCESS",
  "payload": {
    "displayName": "Василий"
  }
}
```

**Ошибка:**

```json
{
  "type": "AUTH_FAILED",
  "payload": {
    "code": "INVALID_PASSWORD",
    "message": "Неверный пароль"
  }
}
```

### Получение списка контактов

**Запрос:**

```json
{
  "type": "GET_CONTACTS"
}
```

**Ответ:**

```json
{
  "type": "CONTACTS_LIST",
  "payload": {
    "contacts": [
      {
        "login": "anna",
        "displayName": "Анна",
        "online": true
      }
    ]
  }
}
```

### Отправка сообщения

**Запрос:**

```json
{
  "type": "SEND_MESSAGE",
  "payload": {
    "to": "anna",
    "text": "Привет!"
  }
}
```

**Подтверждение отправителю:**

```json
{
  "type": "MESSAGE_ACK",
  "payload": {
    "from": "1",
    "to": "anna",
    "text": "Привет!",
    "timestamp": 1234567890
  }
}
```

**Доставка получателю:**

```json
{
  "type": "NEW_MESSAGE",
  "payload": {
    "from": "1",
    "to": "anna",
    "text": "Привет!",
    "timestamp": 1234567890
  }
}
```

### Запрос истории сообщений

**Запрос:**

```json
{
  "type": "GET_HISTORY",
  "payload": {
    "targetLogin": "anna"
  }
}
```

**Ответ:**

```json
{
  "type": "HISTORY",
  "payload": {
    "targetLogin": "anna",
    "messages": [
      {
        "from": "1",
        "to": "anna",
        "text": "Привет!",
        "timestamp": 1234567890
      }
    ]
  }
}
```

### Редактирование сообщения

**Запрос:**

```json
{
  "type": "EDIT_MESSAGE",
  "payload": {
    "to": "anna",
    "originalText": "Привет!",
    "newText": "Привет, как дела?"
  }
}
```

**Уведомление об обновлении:**

```json
{
  "type": "MESSAGE_UPDATED",
  "payload": {
    "originalText": "Привет!",
    "newText": "Привет, как дела?"
  }
}
```

### Удаление сообщения

**Запрос:**

```json
{
  "type": "DELETE_MESSAGE",
  "payload": {
    "to": "anna",
    "text": "Привет!"
  }
}
```

**Уведомление об удалении:**

```json
{
  "type": "MESSAGE_DELETED",
  "payload": {
    "text": "Привет!"
  }
}
```

### Служебные сообщения

**Ping/Pong (проверка соединения):**

```json
// Запрос
{
  "type": "PING"
}

// Ответ
{
  "type": "PONG"
}
```

**Ошибка сервера:**

```json
{
  "type": "ERROR",
  "payload": {
    "code": "UNKNOWN_TYPE",
    "message": "Неизвестный тип: CUSTOM_TYPE"
  }
}
```

## Логирование

Сервер использует SLF4J с Logback. Логи выводятся в консоль в формате:

```
14:35:22 [ws-worker-1] INFO  r.m.s.h.ChatWebSocketHandler - Message sent: 1 → anna
```

### Уровни логирования

| Уровень | Когда использовать                             |
|---------|------------------------------------------------|
| `ERROR` | Критические ошибки, исключения                 |
| `WARN`  | Предупреждения, нештатные ситуации             |
| `INFO`  | Основные события: вход, отправка сообщений     |
| `DEBUG` | Детальная отладка: поиск сообщений, обновления |

### Изменение уровня логирования

В `application.properties`:

```properties
logging.level.ru.moskalev=DEBUG
```

## Разработка

### Добавление нового типа сообщения

1. Обработайте тип в `ChatWebSocketHandler.handleTextMessage()`
2. Добавьте метод-обработчик
3. Реализуйте логику в соответствующем сервисе
4. Добавьте документацию в этот README

## Лицензия

Проект предназначен для учебных целей.