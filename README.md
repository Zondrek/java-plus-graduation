# Explore With Me

Сервис-афиша, где можно найти интересные мероприятия и компанию для участия в них.

## Архитектура

Приложение состоит из четырёх бизнес-сервисов, сервиса статистики и инфраструктуры Spring Cloud:

- **user-service** — управление пользователями. Ни от кого не зависит.
- **event-service** — события и категории. Зависит от user-service и request-service.
- **request-service** — заявки на участие в событиях. Зависит от user-service и event-service.
- **interaction-service** — комментарии и подборки событий. Зависит от всех трёх сервисов выше.
- **stats-server** — сбор и анализ статистики просмотров.

Сервисы общаются через REST (OpenFeign) и находят друг друга через Eureka. Для устойчивости к сбоям подключен Resilience4j. Каждый сервис владеет своими таблицами в БД.

Инфраструктура: Eureka (обнаружение сервисов, порт 8761), Config Server (централизованная конфигурация), Gateway (единая точка входа, порт 8080).

Конфигурации сервисов находятся в `infra/config-server/src/main/resources/configs/`.

## Внутренний API

Сервисы предоставляют внутренний API по пути `/internal/...`, недоступный снаружи через Gateway.

**user-service:**
- `GET /internal/users/{userId}` — получить пользователя по ID
- `GET /internal/users?ids=1,2,3` — получить нескольких пользователей за один запрос

**event-service:**
- `GET /internal/events/{eventId}` — получить данные события
- `GET /internal/events?ids=1,2,3` — получить несколько событий за один запрос
- `PATCH /internal/events/{eventId}/confirmed-requests?delta=1` — изменить счётчик подтверждённых заявок

**request-service:**
- `GET /internal/requests?eventId={id}` — все заявки на событие
- `GET /internal/requests/count?eventId={id}` — количество подтверждённых заявок
- `GET /internal/requests/exists?userId={id}&eventId={id}&status=CONFIRMED` — проверить участие пользователя
- `GET /internal/requests/by-event-and-ids?eventId={id}&requestIds=1,2` — заявки по ID
- `PATCH /internal/requests/bulk-update?eventId={id}&requestIds=1,2&status=CONFIRMED` — массовое обновление статусов
- `PATCH /internal/requests/reject-pending?eventId={id}` — отклонить все ожидающие заявки

## Внешний API

Все запросы идут через Gateway на порт 8080. Спецификации:
- [ewm-main-service-spec.json](ewm-main-service-spec.json) — основной API
- [ewm-stats-service-spec.json](ewm-stats-service-spec.json) — API статистики
