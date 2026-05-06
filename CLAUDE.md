# Spring Boot CRUD — Interview Prep Plan

## How Claude should collaborate on this project

This project covers **Basics of Spring Boot with kafka** — the user is learning by typing, not shipping a feature. Treat Claude as a **guide**, not an implementer:

1. **Before writing any file**, list all files Claude plans to add for the current phase, with a 1–2 line explanation of *what each file teaches* (entity, repository, DTO, etc.).
2. **Wait for the user to pick** which files they will write themselves and which Claude should complete. Do not write any code until the user confirms.
3. For files the user is writing, Claude can answer questions, review, and explain — but should not pre-emptively produce the code.
4. For files Claude completes, keep them small and well-commented for the **why** (interview-relevant trivia like `@Data` on JPA entities, `open-in-view`, etc.).

Goal: Build an Employee CRUD app on Spring Boot 4 + H2 in **4–5 hours** that exercises the Spring/JPA concepts most commonly asked in interviews.

Stack already in `build.gradle`: Spring Boot 4.0.6, Java 26, JPA, H2, Postgres driver, Validation, Security, Actuator, Lombok, DevTools, GraphQL (unused — can drop).

Domain: `Employee` (1) → (N) `Project`.

---

## The 4–5 Hour Plan

| # | Phase | Time | What you get |
|---|---|---|---|
| 1 | **Config, profiles, H2 + Hikari** | 30 min | App boots with proper Hikari pool sizing, H2 console at `/h2-console`, `open-in-view=false`, actuator endpoints |
| 2 | **Employee CRUD + DTO + validation + global error handling** | 45 min | Full REST CRUD: `GET/POST/PUT/DELETE /employees`. `@Valid`, `@Transactional`, `@ControllerAdvice` with `ProblemDetail`, `@Transient` field |
| 3 | **One-to-Many (Project) + N+1 demo & fix** | 45 min | `Employee → List<Project>`, cascade, orphanRemoval. See N+1 in SQL log, fix with `@EntityGraph`. The single most-asked JPA interview topic |
| 4 | **Pagination + `/suggest` autocomplete + caching** | 30 min | `Pageable`, `Page` vs `Slice`, derived query, `@Cacheable` on suggest, `@CacheEvict` on writes |
| 5 | **Auditing + `@Version` optimistic locking** | 30 min | `BaseEntity` with created/updated timestamps, optimistic lock conflict demo |
| 6 | **Tests: `@DataJpaTest`, `@WebMvcTest`, `@SpringBootTest`** | 60 min | One test per slice — must-know for interviews |
| 7 | **(Stretch) Minimal security** | 30 min | Only if time permits. Skippable. |

**Total: ~4 hr 0 min core + 30 min stretch + 30 min buffer = 5 hr ceiling.**

---

## Phase 1 — Config, profiles, H2 + Hikari (30 min)

- `application.yaml` + `application-dev.yaml`
- Hikari pool config: max-pool-size, connection-timeout, idle-timeout, leak-detection-threshold
- H2 console enabled at `/h2-console`
- JPA: `ddl-auto=update`, `show-sql`, `format-sql`, `open-in-view=false` (the correct default — explain why)
- Actuator: expose `health`, `info`, `metrics`
- Brief `@Profile` demo bean
- 1-line note: `@ConfigurationProperties` vs `@Value` (production code uses the former)

## Phase 2 — Employee CRUD + DTO + validation (45 min)

- Entity: `@Entity`, `@Id`, `@GeneratedValue(IDENTITY)`, `@Column`, `@Transient` (computed `fullName`), Lombok `@Getter/@Setter` (skip `@Data` — explain why in 1 line: hashCode/equals on JPA entities)
- `EmployeeRepository extends JpaRepository<Employee, Long>` + 1 derived query
- DTOs: `EmployeeRequest` (`@NotBlank`, `@Email`, `@Size`) + `EmployeeResponse`
- Controller: full CRUD with `@Valid`, `ResponseEntity`, proper status codes (201, 204, 404)
- `@ControllerAdvice` with `ProblemDetail` (RFC 7807) for validation errors + NotFoundException
- Service layer: constructor injection (no `@Autowired`), `@Transactional(readOnly=true)` on reads

## Phase 3 — One-to-Many + N+1 demo & fix (45 min)

- `Project` entity + `@ManyToOne(fetch=LAZY)` to Employee
- `Employee` gets `@OneToMany(mappedBy, cascade=ALL, orphanRemoval=true)`
- `@JsonManagedReference` / `@JsonBackReference` to break serialization cycle
- Endpoint: add projects to an employee
- **Demo N+1**: list endpoint that touches projects → see N+1 in SQL log → fix with `@EntityGraph` or JOIN FETCH
- Mention: `LazyInitializationException` + connection to `open-in-view`

## Phase 4 — Pagination + suggestions + caching (30 min)

- `GET /employees` with `Pageable` (page, size, sort)
- `GET /employees/suggest?q=jo` using `findTop10ByNameStartingWithIgnoreCase`
- `@EnableCaching` on app class
- `@Cacheable("suggest", key="#q")` on suggest method
- `@CacheEvict(value="suggest", allEntries=true)` on create/update/delete
- 1-line note: swap default `ConcurrentMap` for Caffeine in production (TTL + max size)

## Phase 5 — Auditing + optimistic locking (30 min)

- `@EnableJpaAuditing` on app class
- `BaseEntity` with `@CreatedDate`, `@LastModifiedDate`, `@EntityListeners(AuditingEntityListener.class)`
- Add `@Version Long version` to Employee
- Demo `OptimisticLockException` via test (load twice, save both)
- 1-line inline comment: `REQUIRED` vs `REQUIRES_NEW` + self-invocation pitfall (proxy bypass)

## Phase 6 — Tests across all layers (60 min)

- `@DataJpaTest` for repository (derived query + suggest query)
- `@WebMvcTest` for controller with `@MockitoBean` service (validation 400 + 404 + happy path)
- `@SpringBootTest` end-to-end: create employee → add project → list with projects → verify no N+1 (Hibernate Statistics or SQL log assertion)
- Optimistic lock test (load twice, save both → expect `OptimisticLockingFailureException`)

## Phase 7 — Stretch: Minimal security (30 min, skippable)

- `SecurityFilterChain`: permit `/h2-console`, `/actuator/health`; HTTP Basic on the rest
- CSRF disabled with 1-line why-comment (stateless API)
- In-memory user with `BCryptPasswordEncoder`

---

## What's intentionally cut (and why)

| Cut | Reason |
|---|---|
| Pessimistic locking, full transaction propagation lab, Specifications, projections | Diminishing returns; mentioned as 1-line notes in relevant phases |
| `@Async`, `@Scheduled`, `@TransactionalEventListener` | Each is a 30-min rabbit hole. Cover after this is done. |
| Caffeine, second-level Hibernate cache | Default `ConcurrentMap` shows the same annotations; Caffeine is a 1-line config swap |
| Flyway / Liquibase | `ddl-auto=update` is fine for learning H2 |
| MapStruct | Manual mapping is 5 lines and shows you understand the *why* |
| GraphQL | Pulled in `build.gradle` but unused. Drop the dep for a leaner build. |

---

## Discipline rules to hit the timebox

1. **No yak-shaving.** If it works, move on.
2. **One concept per phase gets the deep treatment** (the bolded one). Rest get 1-line interview notes.
3. **You run; I write.** After each phase: start app, hit endpoints, confirm (~2 min/phase). Half of "Spring is confusing" is not seeing it work.
4. **Tests in Phase 6, not interleaved.** Less context-switching; mirrors a realistic exercise.
