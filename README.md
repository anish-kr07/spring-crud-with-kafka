# spring-crud — Setup & Run Guide

A Spring Boot 4 / Java 26 project that demonstrates JPA, REST, Spring Security, and Kafka with the **transactional outbox** pattern.

For deeper conceptual notes see:
- [`spring-crud-guide.html`](spring-crud-guide.html) — Spring/JPA interview-style guide &nbsp;·&nbsp; [📖 preview](https://htmlpreview.github.io/?https://github.com/anish-kr07/spring-crud-with-kafka/blob/main/spring-crud-guide.html)
- [`kafka-guide.html`](kafka-guide.html) — Kafka + outbox + idempotency guide &nbsp;·&nbsp; [📖 preview](https://htmlpreview.github.io/?https://github.com/anish-kr07/spring-crud-with-kafka/blob/main/kafka-guide.html)
- [`docker-compose.md`](docker-compose.md) — Docker/Kafka command reference

---

## Prerequisites

| Tool | Why |
|---|---|
| **Java 26** (JDK) | Project uses Java 26 toolchain (see `build.gradle`). |
| **Docker Desktop** | Runs the local Kafka broker + Kafka UI via `docker-compose.yml`. |
| **curl** | Used by the `api.sh` test script. |
| **An IDE** (optional) | IntelliJ / VS Code for editing. The Gradle wrapper handles builds without one. |

You don't need Gradle installed — the project ships `./gradlew`.

---

## First-time setup (5 minutes)

### 1. Start Docker Desktop

Open Docker Desktop and wait for the whale icon to say **"Engine running"**.

### 2. Pull and start the Kafka stack

```bash
cd spring-crud
docker compose pull
docker compose up -d
```

Verify both containers are up:
```bash
docker compose ps
```

You should see `spring-crud-kafka` and `spring-crud-kafka-ui` both `Up`. Open <http://localhost:8090> for the Kafka UI dashboard.

### 3. Boot the Spring app

```bash
./gradlew bootRun
```

When you see `Tomcat started on port 8080` it's ready.

> **OOM tip**: if the JVM gets SIGKILL'd (exit 137) under memory pressure, run with a smaller heap:
> ```bash
> JAVA_TOOL_OPTIONS='-Xmx384m -Xms256m' ./gradlew bootRun
> ```

### 4. Smoke-test the API

In a second terminal:

```bash
./api.sh dept-list      # 3 seeded departments with employees nested
./api.sh list           # 3 seeded employees
./api.sh dept-get 1     # triggers a Kafka event (DepartmentLookedUp)
./api.sh create         # creates an employee (triggers outbox -> Kafka)
```

In the app log you should see consumer messages like:
```
findByDeprtId was called — key=1 partition=0 offset=0 payload={"departmentId":1,...}
Employee event received — event-type=EmployeeCreated key=4 partition=0 offset=0 payload={...}
```

(Note: the outbox relay polls every **60 seconds** by default in this project — see `OutboxRelay.java:62`. Reduce to `1000` for snappy local testing.)

---

## What's running where

| Service | Port | URL |
|---|---|---|
| Spring Boot app | 8080 | <http://localhost:8080> |
| H2 console | 8080 | <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:employeesdb`, user `sa`, blank password) |
| Kafka broker | 9092 | (TCP — not a web UI) |
| Kafka UI | 8090 | <http://localhost:8090> |

**HTTP Basic auth** for API endpoints: `user` / `password`. The current `SecurityConfig` has `permitAll`, so credentials are accepted but not required.

---

## API cheat sheet (all via `./api.sh`)

```bash
./api.sh help                       # full command list

# Employees
./api.sh list                       # GET /v1/employees
./api.sh get 1                      # GET /v1/employees/1
./api.sh search ada@example.com 0   # GET /v1/employees/search?email=...&minSalary=...
./api.sh create [deptId]            # POST /v1/employees (random email, deptId default 1)
./api.sh put 1 [deptId]             # PUT /v1/employees/1 (full replace)
./api.sh patch 1 '{"salary":160000}' # PATCH /v1/employees/1
./api.sh delete 1                   # DELETE /v1/employees/1

# Departments
./api.sh dept-list                  # GET /v1/departments (with employees nested)
./api.sh dept-get 1                 # GET /v1/departments/1
./api.sh dept-create "Marketing"    # POST /v1/departments

# Error-path probes (each hits a specific @ExceptionHandler)
./api.sh err-404                    # NotFoundException -> 404
./api.sh err-bad-json               # malformed JSON -> 400
./api.sh err-bad-date               # bad date format -> 400
./api.sh err-409                    # duplicate email -> 409
./api.sh err-validation             # validation failure -> 400
./api.sh verify                     # run all error probes in sequence
```

---

## Docker / Kafka stack management

```bash
# Start / stop the stack
docker compose up -d                 # start in background
docker compose ps                    # show containers + health
docker compose stop                  # stop containers (data kept)
docker compose down                  # stop + remove containers
docker compose down -v               # full wipe including volumes

# Logs
docker compose logs -f               # tail all services
docker compose logs -f kafka         # just the broker
docker compose logs --tail=50 kafka  # last 50 lines

# Restart a single service
docker compose restart kafka

# Remove and re-pull (after image tag changes)
docker compose down
docker compose pull
docker compose up -d
```

Full reference: [`docker-compose.md`](docker-compose.md).

---

## Inspecting Kafka from the terminal

All commands use the broker container's bundled CLI tools at `/opt/kafka/bin/`. Run them via `docker exec`.

### List, describe, create topics

```bash
# list topics
docker exec spring-crud-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# describe (shows partitions, leader, replicas, configs)
docker exec spring-crud-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe --topic employee.events

# create explicitly (auto-create is on so this is usually unnecessary)
docker exec spring-crud-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --topic some.new.topic --partitions 3 --replication-factor 1
```

### Tail messages live (`kafka-console-consumer`)

```bash
# tail with key + headers visible
docker exec -it spring-crud-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic employee.events \
  --from-beginning \
  --property print.key=true \
  --property print.headers=true \
  --property key.separator=" | "

# same for departments
docker exec -it spring-crud-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic department.events \
  --from-beginning \
  --property print.key=true \
  --property print.headers=true \
  --property key.separator=" | "
```

> Use `--from-beginning` only if you want history. Without it, you only see messages produced *after* you start the consumer.

### Send a test message (`kafka-console-producer`)

```bash
docker exec -i spring-crud-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic employee.events \
  <<< 'manual test message'
```

The Spring listener will pick this up and log it (under `Employee event received` if you target the employee topic).

### Inspect consumer groups + lag

```bash
# list groups
docker exec spring-crud-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --list

# describe — shows current offset, log-end offset, and LAG per partition
docker exec spring-crud-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group spring-crud
```

A growing **LAG** column means the consumer can't keep up with the producer.

### Alter a topic's retention

```bash
# 1 day retention on employee.events
docker exec spring-crud-kafka /opt/kafka/bin/kafka-configs.sh \
  --bootstrap-server localhost:9092 \
  --entity-type topics --entity-name employee.events \
  --alter --add-config retention.ms=86400000

# revert to broker default
docker exec spring-crud-kafka /opt/kafka/bin/kafka-configs.sh \
  --bootstrap-server localhost:9092 \
  --entity-type topics --entity-name employee.events \
  --alter --delete-config retention.ms
```

---

## Inspecting the database

The H2 in-memory DB lives only for the JVM's lifetime (data is wiped on app restart).

1. Open <http://localhost:8080/h2-console>
2. **JDBC URL**: `jdbc:h2:mem:employeesdb`
3. **User**: `sa`  &nbsp; **Password**: *(blank)*
4. Click **Connect**

Useful queries:
```sql
-- core tables
SELECT * FROM departments;
SELECT * FROM employees;

-- transactional outbox — published_at is NULL until the relay drains it
SELECT id, aggregate_type, aggregate_id, event_type, created_at, published_at
FROM outbox_events
ORDER BY id DESC;

-- count unpublished outbox rows
SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL;
```

---

## Project layout

```
spring-crud/
├── api.sh                         # shell wrapper around curl for all endpoints
├── build.gradle                   # Spring Boot 4 + spring-kafka + Lombok + H2 + Postgres + JPA
├── docker-compose.yml             # local Kafka (apache/kafka:3.9.0, KRaft) + Kafka UI
├── docker-compose.md              # docker/kafka command reference
├── kafka-guide.html               # Kafka concepts deep-dive (interview-style)
├── spring-crud-guide.html         # Spring/JPA concepts deep-dive
├── README.md                      # this file
└── src/main/java/com/example/spring_crud/
    ├── SpringCrudApplication.java
    ├── config/
    │   ├── DataSeeder.java        # @Profile dev/test seed data
    │   └── SecurityConfig.java    # http basic, csrf disabled, all permit
    ├── controller/                # REST controllers (v1 / v2 employees, departments)
    ├── service/                   # business logic; @Transactional boundaries
    ├── repository/                # JpaRepository interfaces
    ├── entity/                    # JPA entities (Employee, Department, OutboxEvent)
    ├── dto/                       # request + response DTOs
    ├── exception/                 # NotFoundException + GlobalExceptionHandler
    ├── kafka/                     # KafkaConfig, KafkaTopics, publishers, listeners
    ├── outbox/                    # OutboxPublisher + OutboxRelay (scheduled poller)
    ├── event/                     # event payload classes (DepartmentEvent)
    └── common/                    # DobFormat meta-annotation
```

---

## Common workflows

### "I changed Kafka properties — how do I restart cleanly?"

```bash
lsof -ti:8080 | xargs kill -9 2>/dev/null    # kill old Spring app
./gradlew bootRun                            # restart with new config
```

(No need to restart the broker for app-side changes.)

### "I want to wipe Kafka state"

```bash
docker compose down -v && docker compose up -d
```

This removes the broker's storage so every topic starts empty. The Spring app will re-create topics on first publish (auto-create is on).

### "I want to wipe DB state"

Restart the app — H2 is in-memory, all rows go away.

### "I want to drop all unpublished outbox events"

```sql
DELETE FROM outbox_events WHERE published_at IS NULL;
```
…or just restart the app (H2 in-memory).

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Cannot connect to Docker daemon` | Docker Desktop not running | Start Docker Desktop, wait for "Engine running" |
| `image not found: bitnami/kafka:3.7` | Bitnami restructured catalog | This project uses `apache/kafka:3.9.0` instead — re-pull |
| `Connection to node -1 (localhost/127.0.0.1:9092) could not be established` | Broker not running | `docker compose ps`; restart with `docker compose up -d` |
| `bootRun` killed with exit 137 (SIGKILL) | OS killed JVM under memory pressure | Run with smaller heap: `JAVA_TOOL_OPTIONS='-Xmx384m' ./gradlew bootRun` |
| `Required bean of type 'KafkaTemplate' not found` | Auto-config generic mismatch (`<?, ?>` vs `<String, String>`) | Already fixed in `kafka/KafkaConfig.java` — typed beans declared explicitly |
| Outbox row stays `published_at = NULL` for ages | Relay poll interval is `60_000` ms in dev | Edit `OutboxRelay.java:62` → `fixedDelay = 1000` for fast polling |
| 401 Unauthorized on every request | `SecurityConfig` not registered (missing `@Configuration`) | Already fixed — `@Configuration` + `@EnableWebSecurity` are present |
| Two listeners share a topic, one stays silent | Same `groupId` + same topic → Kafka assigns partitions to one consumer | Use different `groupId`s, or different topics. See § 4.1 of `kafka-guide.html` |
| 400 with `"Cannot deserialize ... LocalDate"` | Sending wrong date format | Use `yyyy-MM-dd` for `joinDate` and `dateOfBirth` (the global Jackson format wins over `@DobFormat` in this Boot 4 setup) |

---

## Architectural quick reference

```
Client → POST /v1/employees
   ↓
[EmployeeController] → [EmployeeService @Transactional]
                          ├── employeeRepo.save(e)
                          └── outboxPublisher.record(...)   ← @Propagation.MANDATORY
                              (both committed atomically — same DB tx)
                                   │
                                   ▼
                        every 60s [OutboxRelay @Scheduled]
                                   ├── SELECT FOR UPDATE oldest unpublished
                                   ├── kafkaTemplate.send().get(10s)   ← sync
                                   └── set published_at = now()
                                                │
                                                ▼
                                  Kafka topic "employee.events"
                                                │
                                                ▼
                                   [EmployeeEventListener] (separate thread)
                                                    │
                                                    └── log it / project it / etc.

Client → GET /v1/departments/1
   ↓
[DepartmentController] → [DepartmentService @Transactional(readOnly)]
                            ├── load entity
                            └── departmentEventPublisher.publishLookup(dept)
                                  ↓
                                kafkaTemplate.send().whenComplete(...)   ← async
                                  ↓
                           Kafka topic "department.events"
                                  ↓
                           [DepartmentEventListener] (switch on event-type header)
```
