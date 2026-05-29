# AI Support Platform: support-api Learning Guide

This guide explains the code currently written for the `support-api` Spring Boot service. It is grounded in the files under `support-api/src/main/java/com/krish/supportapi`, the repository interfaces, the Liquibase migrations, and the YAML configuration files.

One important project note: the domain and repository code uses the package `com.krish.supportapi`, but the current application class is in `support_api.SupportApiApplication`. In Spring Boot, component scanning normally starts from the package of the main application class. For this project to automatically discover `com.krish.supportapi` components, the application class should eventually be moved under `com.krish.supportapi` or configured to scan that package.

## Topic 1 - Project Structure

Your service is using a layered architecture. The goal is to split the application into focused layers so each part has one clear job.

Current and intended structure:

```text
support-api
  src/main/java
    com/krish/supportapi
      domain
        entity
        enums
        dto
          request
          response
      repository
      service        future layer
      controller     future layer
  src/main/resources
    application.yaml
    application-local.yml
    db/changelog
```

The layers work like this:

- Controller layer: receives HTTP requests, validates request DTOs, calls services, and returns response DTOs.
- Service layer: contains business rules. For example, create a ticket, assign a ticket, revoke refresh tokens, or calculate analytics.
- Repository layer: talks to the database through Spring Data JPA. Your repositories are interfaces like `UserRepository` and `TicketRepository`.
- Domain layer: contains the core objects your system works with: entities, enums, and DTOs.
- Database migration layer: Liquibase files create and evolve the PostgreSQL schema.
- Configuration layer: YAML files tell Spring how to connect to PostgreSQL, Redis, Kafka, Liquibase, JWT settings, OpenAI settings, and observability tools.

The dependency direction should be one-way:

```text
Controller -> Service -> Repository -> Entity/Database
Controller -> DTOs
Service -> DTOs, Entities, Repositories
Repository -> Entities
Entities -> Enums
```

The lower layers should not depend on upper layers. For example:

- A repository should not know about controllers.
- An entity should not know about HTTP requests.
- DTOs should not contain database behavior.
- Services should coordinate repositories and business rules.

We separate packages because each package has a different responsibility:

- `domain.entity`: database-mapped Java classes.
- `domain.enums`: fixed allowed values shared by entities and DTOs.
- `domain.dto.request`: incoming API payload shapes.
- `domain.dto.response`: outgoing API response shapes.
- `repository`: database access interfaces.

This separation makes the code easier to test, easier to change, and easier to explain. If a controller changes its API response shape, the database entity does not need to change. If a database column changes, the request DTO does not automatically change.

## Topic 2 - Entities and JPA Annotations

JPA stands for Java Persistence API. It is the standard Java way to map Java objects to relational database tables. In Spring Boot, Hibernate is the JPA implementation that does the real work.

In your code:

- `User` maps to `public.users`.
- `Ticket` maps to `public.tickets`.
- `TicketMessage` maps to `public.ticket_messages`.
- `AiResponseAudit` maps to `public.ai_response_audit`.
- `RefreshToken` maps to `public.refresh_tokens`.

JPA lets you work with Java objects while Hibernate generates SQL behind the scenes.

Example:

```java
Optional<User> user = userRepository.findByEmail("customer@example.com");
```

Spring Data JPA turns this into a database query against the `users` table.

### How Java objects map to database tables

Your `Ticket` entity has Java fields like this:

```java
private String ticketNumber;
private TicketStatus status;
private User customer;
```

The database table has columns like this:

```sql
ticket_number VARCHAR(20)
status public.ticket_status
customer_id UUID
```

JPA annotations explain how the Java fields connect to the database columns.

### `@Entity`

Used in:

- `User`
- `Ticket`
- `TicketMessage`
- `AiResponseAudit`
- `RefreshToken`

What it does:

- Marks a class as a JPA-managed persistent object.
- Tells Hibernate this class represents a database row.

Why we use it:

- Without `@Entity`, Spring Data JPA will not treat the class as something that can be saved, queried, or mapped from a table.

What breaks if removed:

- Repositories like `JpaRepository<User, UUID>` would fail because `User` would not be a managed JPA type.

### `@Table`

Example:

```java
@Table(name = "tickets", schema = "public")
```

What it does:

- Connects the entity class to a specific database table.
- Your entities explicitly map to tables in the `public` schema.

Why we use it:

- Java class names and table names do not always match.
- `TicketMessage` maps to `ticket_messages`, not `ticket_message`.
- Explicit schema mapping avoids ambiguity.

What breaks if removed:

- Hibernate would infer table names from class names.
- Some inferred names might not match your Liquibase-created tables.
- Schema validation could fail because Hibernate would look for the wrong table.

### `@Id`

Example:

```java
@Id
private UUID id;
```

What it does:

- Marks the primary key field of the entity.

Why we use it:

- JPA needs one field that uniquely identifies each database row.

What breaks if removed:

- Hibernate cannot manage the entity because every entity must have an identifier.
- The application would fail during startup mapping validation.

### `@GeneratedValue(strategy = GenerationType.AUTO)`

Example:

```java
@GeneratedValue(strategy = GenerationType.AUTO)
private UUID id;
```

What it does:

- Tells Hibernate the primary key value is generated automatically.
- Your database also defaults UUIDs with `gen_random_uuid()`.

Why we use it:

- Code creating a new `User` or `Ticket` should not manually invent IDs.

What breaks if removed:

- When saving new entities, you may need to provide IDs yourself.
- Missing IDs can cause persistence errors.

### `@Column`

Example:

```java
@Column(name = "password_hash", nullable = false, length = 255)
private String passwordHash;
```

What it does:

- Maps a Java field to a database column.
- Allows explicit control over column name, nullability, uniqueness, length, precision, scale, and column type.

Why we use it:

- Java uses camelCase: `passwordHash`.
- PostgreSQL uses snake_case: `password_hash`.
- Explicit mapping removes guesswork.

What breaks if removed:

- Hibernate would infer column names.
- Some fields might still work depending on naming strategy, but the mapping becomes less obvious and more fragile.
- Schema validation can fail if inferred names differ from actual Liquibase column names.

### `@ManyToOne`

Examples:

```java
private User customer;
private User assignedAgent;
private Ticket ticket;
private User sender;
private User user;
```

What it does:

- Defines a many-to-one relationship.
- Many tickets can belong to one customer.
- Many messages can belong to one ticket.
- Many refresh tokens can belong to one user.

Why we use it:

- The database stores foreign key IDs like `customer_id`, but Java code should work with objects like `User customer`.

What breaks if removed:

- JPA would not understand the relationship.
- A field like `private User customer` would not map correctly to `customer_id`.
- You would lose relationship navigation such as `ticket.getCustomer().getEmail()`.

### `@JoinColumn`

Example:

```java
@JoinColumn(
    name = "customer_id",
    nullable = false,
    foreignKey = @ForeignKey(name = "fk_tickets_customer_id")
)
private User customer;
```

What it does:

- Specifies which database column stores the foreign key.

Why we use it:

- `Ticket.customer` is stored in the `tickets.customer_id` column.
- `Ticket.assignedAgent` is stored in `tickets.assigned_agent_id`.
- `TicketMessage.ticket` is stored in `ticket_messages.ticket_id`.

What breaks if removed:

- Hibernate would guess the join column name.
- Guessed names may not match the Liquibase schema.
- Relationships could fail schema validation or query generation.

### `@ForeignKey`

Example:

```java
foreignKey = @ForeignKey(name = "fk_ticket_messages_ticket_id")
```

What it does:

- Names the foreign key constraint in the JPA mapping.

Why we use it:

- Your Liquibase migrations created explicit constraint names.
- The entity mapping mirrors the database design.

What breaks if removed:

- Runtime queries may still work, but schema generation/validation metadata becomes less explicit.
- If Hibernate ever generated DDL in another environment, it could create constraint names that differ from your standard.

### `@Enumerated(EnumType.STRING)`

Example:

```java
@Enumerated(EnumType.STRING)
private TicketStatus status;
```

What it does:

- Stores enum values by name, such as `OPEN` or `RESOLVED`.

Why we use it:

- Enum names are stable and readable.
- The database enum values are also strings like `OPEN`, `IN_PROGRESS`, and `CUSTOMER`.

What breaks if removed:

- JPA may default to ordinal mapping, where `OPEN` is stored as `0`, `RESOLVED` as another number, and so on.
- Ordinal values are dangerous because adding or reordering enum constants can corrupt meaning.

### `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`

Used on PostgreSQL enum fields:

- `User.role`
- `Ticket.status`
- `Ticket.priority`
- `Ticket.category`
- `Ticket.aiSuggestedCategory`

What it does:

- Tells Hibernate 6 to treat the field as a named database enum type instead of a plain string column.

Why we use it:

- Your database columns are PostgreSQL enum types like `public.ticket_status`, not simple `VARCHAR` columns.
- This helps Hibernate bind enum values correctly when talking to PostgreSQL.

What breaks if removed:

- Hibernate may try to bind enum values as regular strings.
- PostgreSQL can reject inserts or updates because a `VARCHAR` value is not automatically accepted as a custom enum type in all contexts.

### `@PrePersist`

Example:

```java
@PrePersist
protected void onCreate() {
    if (createdAt == null) {
        createdAt = LocalDateTime.now();
    }
}
```

What it does:

- Runs the method right before JPA inserts a new row.

Why we use it:

- Your entities use it to set `createdAt`, `updatedAt`, and default enum values before saving.
- This keeps Java-created entities consistent with database defaults.

What breaks if removed:

- If Java sends null values for non-null columns like `created_at`, inserts may fail.
- Some defaults might still be handled by PostgreSQL, but relying on both Java and database defaults makes entity objects accurate immediately after save.

### `@PreUpdate`

Used in:

- `User`
- `Ticket`

What it does:

- Runs the method before JPA updates an existing row.

Why we use it:

- It updates the `updatedAt` field whenever the entity changes.

What breaks if removed:

- Updated rows may keep an old `updated_at` timestamp.
- You lose an important audit signal for users and tickets.

### `@Builder.Default`

Used with Lombok builders:

```java
@Builder.Default
private TicketStatus status = TicketStatus.OPEN;
```

What it does:

- Preserves the field initializer when creating an object through Lombok's builder.

Why we use it:

- Without it, `Ticket.builder().build()` would set `status` to null even though the field initializer says `OPEN`.

What breaks if removed:

- Builder-created objects could lose default values.
- Inserts could fail for non-null fields like `User.role`, `User.isActive`, `Ticket.status`, and `Ticket.priority`.

### `FetchType.LAZY`

Example:

```java
@ManyToOne(fetch = FetchType.LAZY)
private User assignedAgent;
```

What it does:

- Delays loading the related object until code actually accesses it.

Why we use it:

- A ticket query should not automatically load every related customer, assigned agent, messages, and audit records unless needed.
- It reduces unnecessary database work.

What breaks if removed:

- `ManyToOne` defaults to eager loading in JPA.
- Queries can become slower because related rows are fetched automatically.
- In production, eager loading can create large hidden query chains and performance problems.

### LAZY vs EAGER

LAZY means:

- Load the main entity now.
- Load the related entity later, only if accessed.

EAGER means:

- Load the related entity immediately with the main entity.

For production systems, LAZY is usually preferred because APIs often need only part of the object graph. For example, a ticket list page may need ticket IDs, titles, status, and created time. It does not always need the full customer object.

## Topic 3 - Lombok Annotations

Lombok is a compile-time tool that generates repetitive Java code for you. It reduces boilerplate like getters, setters, constructors, and builders.

### `@Getter`

What it does:

- Generates getter methods for fields.

Why we use it:

- Services and mappers need to read entity fields.

If removed:

- Code that calls methods like `ticket.getStatus()` would not compile unless you manually wrote getters.

### `@Setter`

What it does:

- Generates setter methods for fields.

Why we use it:

- JPA and service code often need to update entity fields.

If removed:

- Code that calls methods like `ticket.setStatus(TicketStatus.RESOLVED)` would not compile unless you manually wrote setters.

### `@NoArgsConstructor`

What it does:

- Generates a constructor with no arguments.

Why we use it:

- JPA requires a no-argument constructor so it can create entity instances when reading database rows.
- DTOs also use it for JSON deserialization.

If removed:

- JPA entity loading can fail.
- Request DTO binding from JSON may fail.

### `@AllArgsConstructor`

What it does:

- Generates a constructor with every field as an argument.

Why we use it:

- Useful for tests, mapping, and simple object creation.

If removed:

- Builder still works, but code expecting an all-fields constructor would not compile.

### `@Builder`

What it does:

- Generates a fluent builder API for creating objects.

Why we use it:

- Objects with many fields are easier and safer to create with named builder methods.

Example:

```java
Ticket ticket = Ticket.builder()
    .ticketNumber("TCK-1001")
    .title("Cannot log in")
    .description("Customer cannot access account")
    .status(TicketStatus.OPEN)
    .priority(TicketPriority.HIGH)
    .customer(customer)
    .build();
```

If removed:

- Code using `Ticket.builder()` would not compile.
- You would need constructors or setters instead.

### `@Data`

Used on DTOs, not entities.

What it does:

- Combines getters, setters, `toString()`, `equals()`, `hashCode()`, and required constructor behavior.

Why we use it on DTOs:

- DTOs are simple data containers.
- They do not represent live database relationships.

If removed:

- JSON serialization/deserialization and mapping code may need manually written getters and setters.

### Why entities use `@Getter` and `@Setter`, not `@Data`

Entities are not simple data bags. They are database-managed objects with identity and relationships.

`@Data` generates `equals()`, `hashCode()`, and `toString()` using fields. On entities, that can be risky:

- It may accidentally traverse LAZY relationships.
- It may trigger database queries unexpectedly.
- It can cause recursion, such as `Ticket -> User -> Tickets -> User`.
- It can make equality depend on mutable fields.

Using `@Getter` and `@Setter` is safer for JPA entities.

## Topic 4 - Enums and PostgreSQL Enum Types

An enum is a fixed set of allowed values.

Your Java enums are:

- `UserRole`: `ADMIN`, `AGENT`, `CUSTOMER`
- `TicketStatus`: `OPEN`, `IN_PROGRESS`, `AI_PROCESSING`, `WAITING_CUSTOMER`, `RESOLVED`, `CLOSED`, `ESCALATED`
- `TicketPriority`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `TicketCategory`: `BILLING`, `TECHNICAL`, `ACCOUNT`, `SHIPPING`, `GENERAL`, `COMPLAINT`, `FEATURE_REQUEST`

Enums help because they prevent invalid values in Java code.

Instead of this:

```java
ticket.setStatus("DONE_MAYBE");
```

Your code must do this:

```java
ticket.setStatus(TicketStatus.RESOLVED);
```

PostgreSQL also has enum types in your migrations:

```sql
CREATE TYPE public.ticket_status AS ENUM (
    'OPEN',
    'IN_PROGRESS',
    'AI_PROCESSING',
    'WAITING_CUSTOMER',
    'RESOLVED',
    'CLOSED',
    'ESCALATED'
);
```

This means invalid values are blocked at two levels:

- Java blocks invalid enum constants at compile time.
- PostgreSQL blocks invalid stored values at the database level.

### Why define enums in both Java and SQL?

Java enums protect application code. PostgreSQL enums protect persisted data.

If only Java had enums, someone could still insert invalid values directly into the database.

If only PostgreSQL had enums, Java code would pass around plain strings and could make mistakes before the insert.

Using both keeps application logic and database integrity aligned.

### Role of `@Enumerated(EnumType.STRING)`

This tells JPA to store enum names like `CUSTOMER`, not numeric positions like `2`.

This is important because string values are stable and readable.

### Role of `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`

This tells Hibernate that the database column is a real PostgreSQL named enum type, such as `public.user_role`.

Together, these two annotations say:

- Use the Java enum name.
- Bind it to a PostgreSQL enum column correctly.

## Topic 5 - DTOs

DTO means Data Transfer Object. A DTO is an object used to move data into or out of the API.

Your request DTOs are:

- `RegisterRequest`
- `LoginRequest`
- `CreateTicketRequest`
- `UpdateTicketStatusRequest`
- `AssignTicketRequest`
- `CreateMessageRequest`

Your response DTOs are:

- `AuthResponse`
- `TicketResponse`
- `TicketDetailResponse`
- `MessageResponse`
- `UserResponse`
- `AnalyticsOverviewResponse`

### DTO vs Entity

An entity maps to a database table. A DTO maps to an API request or response.

Example:

- `User` entity has `passwordHash`.
- `UserResponse` does not expose `passwordHash`.

That is intentional. The API should never return password hashes.

### Why never expose entities directly?

Exposing entities directly is risky because:

- You may leak sensitive fields like `passwordHash` or `tokenHash`.
- LAZY relationships may trigger unexpected database queries during JSON serialization.
- API responses become tightly coupled to database tables.
- Changing the database schema can accidentally break API clients.

DTOs let you choose exactly what the API accepts and returns.

### Request DTOs

Request DTOs represent incoming client input.

Example:

```java
public class LoginRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
```

This says a login request must have a valid email and a non-blank password.

### Response DTOs

Response DTOs represent outgoing API data.

Example:

```java
public class AuthResponse {
    private UUID userId;
    private String email;
    private String fullName;
    private UserRole role;
    private String accessToken;
    private String refreshToken;
}
```

This gives the client authentication results without exposing the full `User` entity.

### Validation annotations

#### `@NotBlank`

Used on strings like email, password, full name, title, description, and message content.

What it does:

- Rejects null, empty, or whitespace-only strings.

Why we use it:

- A ticket title of `"   "` should not be accepted.

If removed:

- Invalid blank input could enter the service layer.

#### `@Email`

Used on email fields.

What it does:

- Checks that the string looks like an email address.

Why we use it:

- Authentication and registration depend on valid email identifiers.

If removed:

- Clients could submit values that are not usable emails.

#### `@Size`

Used on:

- `RegisterRequest.password` with `min = 8`
- `CreateTicketRequest.title` with `max = 500`
- `CreateMessageRequest.content` with `max = 5000`

What it does:

- Enforces length limits.

Why we use it:

- The database has limits such as `title VARCHAR(500)`.
- Passwords should have a minimum length.

If removed:

- Very long values could fail later at the database layer.
- Weak short passwords could be accepted.

#### `@NotNull`

Used on:

- `UpdateTicketStatusRequest.status`
- `AssignTicketRequest.agentId`

What it does:

- Rejects null values.

Why we use it:

- Updating a ticket status without a status makes no business sense.
- Assigning a ticket without an agent ID cannot work.

If removed:

- The service layer would need extra manual null checks.

### How validation is triggered in Spring Boot

Validation is usually triggered in a controller by adding `@Valid` to a request body parameter.

Example:

```java
public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.ok(authService.register(request));
}
```

Because your project includes `spring-boot-starter-validation`, Spring can read Jakarta validation annotations and reject bad input before it reaches business logic.

## Topic 6 - Repository Layer

Spring Data JPA repositories are interfaces that define database operations.

Your repositories are:

- `UserRepository`
- `TicketRepository`
- `TicketMessageRepository`
- `AiResponseAuditRepository`
- `RefreshTokenRepository`

Each extends `JpaRepository<Entity, UUID>`.

Example:

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

### What `JpaRepository` gives for free

By extending `JpaRepository`, you automatically get methods like:

- `save(entity)`
- `findById(id)`
- `findAll()`
- `delete(entity)`
- `deleteById(id)`
- `existsById(id)`
- `count()`
- paginated and sorted find operations

You do not write SQL for these standard operations.

### How Spring implements repositories at runtime

You only write interfaces. Spring Data JPA creates proxy implementations at application startup.

When Spring sees:

```java
public interface TicketRepository extends JpaRepository<Ticket, UUID>
```

it creates an object that implements that interface and wires it into services.

That is why you do not create classes like `TicketRepositoryImpl` for normal repository methods.

### Method name conventions

Spring Data JPA can read method names and generate queries.

Examples from your code:

- `findByEmail(String email)` queries users by `email`.
- `existsByEmail(String email)` checks whether a user exists.
- `findByStatus(TicketStatus status, Pageable pageable)` queries tickets by status.
- `findByCustomerId(UUID customerId, Pageable pageable)` queries by `ticket.customer.id`.
- `findByTicketId(UUID ticketId, Pageable pageable)` queries by `ticketMessage.ticket.id`.
- `findByUserIdAndRevokedFalse(UUID userId)` queries refresh tokens by `user.id` and `revoked = false`.

Spring translates these method names into JPQL, then Hibernate turns that into SQL.

### `@Query` and JPQL

`@Query` lets you write a custom query when method names are not enough.

Example:

```java
@Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = :status")
long countTotalByStatus(@Param("status") TicketStatus status);
```

JPQL is Java Persistence Query Language. It looks similar to SQL, but it uses entity names and Java field names, not table names and column names.

JPQL:

```java
SELECT COUNT(t) FROM Ticket t WHERE t.status = :status
```

SQL would refer to the table and column:

```sql
SELECT COUNT(*) FROM public.tickets WHERE status = ?
```

Use JPQL when:

- A method name would be too long or unclear.
- You need aggregation like `COUNT` or `SUM`.
- You need custom filtering.

Your current custom repository queries include:

- Count tickets by status.
- Count tickets resolved after a provided `todayMidnight`.
- Fetch created/resolved timestamp pairs for service-layer average resolution time calculation.
- Count tickets where AI did not escalate and status is `RESOLVED`.
- Sum all AI tokens.
- Find failed AI audit rows.

### `@Param`

Example:

```java
@Query("SELECT COUNT(t) FROM Ticket t WHERE t.resolvedAt >= :todayMidnight")
long countResolvedToday(@Param("todayMidnight") LocalDateTime todayMidnight);
```

What it does:

- Binds a Java method parameter to a named JPQL parameter.

Why we use it:

- It makes the query clear and prevents parameter-order mistakes.

If removed:

- Spring may not know which method parameter should bind to `:todayMidnight`, especially when compiler parameter metadata is unavailable.

### `Page` and `Pageable`

Pagination means returning data in chunks instead of returning everything at once.

Your ticket and message repositories use:

```java
Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);
```

`Pageable` tells Spring:

- Which page number to load.
- How many records per page.
- What sort order to use.

`Page` returns:

- The content for the current page.
- Total elements.
- Total pages.
- Whether there is a next page.

Why it matters:

- A production system may have thousands or millions of tickets.
- Returning all tickets in one response is slow and memory-heavy.

### Why no `@Transactional` on repositories

Repositories should focus on database access, not business transaction boundaries.

Transactions belong in the service layer because one business operation may involve multiple repositories.

Example:

```text
Create ticket:
  save ticket
  save first message
  publish Kafka event
```

That whole operation should succeed or fail together. The service layer is the right place to mark that boundary.

## Topic 7 - Spring Security and `UserDetails`

Your `User` entity implements Spring Security's `UserDetails`.

Why:

- Spring Security needs a standard way to read user identity, password, roles, and account state.
- By implementing `UserDetails`, your `User` entity can be used during authentication.

### `getAuthorities()`

```java
return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
```

What it does:

- Converts your `UserRole` enum into a Spring Security authority.

Why we use it:

- Spring Security authorization checks work with authorities, not directly with your enum.

If role is `ADMIN`, the authority becomes:

```text
ROLE_ADMIN
```

### `getPassword()`

```java
return passwordHash;
```

What it does:

- Gives Spring Security the stored hashed password.

Why we use it:

- During login, Spring compares the raw submitted password to this stored hash using a password encoder.

### `getUsername()`

```java
return email;
```

What it does:

- Tells Spring Security that email is the login username.

Why we use it:

- Your users table has unique emails.
- The login DTO asks for email and password.

### `isAccountNonExpired()`

Returns `true`.

What it means:

- Accounts do not currently expire in your model.

If it returned `false`:

- Spring Security would reject login even if the password is correct.

### `isAccountNonLocked()`

Returns `true`.

What it means:

- Accounts are not currently locked by a separate lock flag.

If it returned `false`:

- Spring Security would block authentication.

### `isCredentialsNonExpired()`

Returns `true`.

What it means:

- Password credentials do not currently expire in your model.

If it returned `false`:

- Spring Security would require credential renewal and block normal login.

### `isEnabled()`

```java
return isActive;
```

What it does:

- Uses your `users.is_active` column to decide whether the account is enabled.

Why we use it:

- Disabled users should not be allowed to authenticate.

### `GrantedAuthority` and `SimpleGrantedAuthority`

`GrantedAuthority` is Spring Security's interface for a permission or role.

`SimpleGrantedAuthority` is a basic implementation that stores the authority as a string.

Your code turns this:

```java
UserRole.ADMIN
```

into this:

```java
new SimpleGrantedAuthority("ROLE_ADMIN")
```

### Why prefix with `ROLE_`

Spring Security has role helpers that expect this convention.

For example, a check for role `ADMIN` usually maps to authority `ROLE_ADMIN`.

If you stored just `ADMIN`, role-based checks may not match unless you customize the security configuration.

### What Spring Security does at runtime

At login time, a future authentication service will usually:

1. Load the user by email using `UserRepository.findByEmail`.
2. Give the `UserDetails` object to Spring Security.
3. Compare the submitted password to `passwordHash`.
4. Check account state methods like `isEnabled()`.
5. Read authorities from `getAuthorities()`.
6. Create JWT tokens if authentication succeeds.

## Topic 8 - Liquibase Database Migrations

Liquibase is a database migration tool. It creates and updates your database schema in a controlled, repeatable way.

Your master changelog is:

```text
support-api/src/main/resources/db/changelog/master.xml
```

It includes six SQL migration files in order:

1. `001-create-users.sql`
2. `002-create-tickets.sql`
3. `003-create-messages.sql`
4. `004-create-ai-audit.sql`
5. `005-create-refresh-tokens.sql`
6. `006-create-indexes.sql`

### Why order matters

Users must exist before tickets because `tickets.customer_id` references `users.id`.

Tickets must exist before ticket messages because `ticket_messages.ticket_id` references `tickets.id`.

Indexes come last because they reference tables and columns that must already exist.

### Liquibase formatted SQL

Each SQL file starts with:

```sql
--liquibase formatted sql
```

This tells Liquibase to parse the SQL file as a Liquibase changelog, not as a plain SQL dump.

Each file has a changeset header:

```sql
--changeset krish:001-create-users
```

A changeset is one versioned database change. Liquibase tracks each changeset in its own database tracking table.

The changeset identity is:

```text
author:id
```

For example:

```text
krish:001-create-users
```

### What your migrations create

`001-create-users.sql`:

- Enables `pgcrypto` for `gen_random_uuid()`.
- Creates `user_role`.
- Creates `users`.

`002-create-tickets.sql`:

- Creates ticket enums.
- Creates `tickets`.
- Adds foreign keys to `users`.

`003-create-messages.sql`:

- Creates `ticket_messages`.
- Adds foreign keys to `tickets` and `users`.
- Uses `ON DELETE CASCADE` for ticket messages when a ticket is deleted.

`004-create-ai-audit.sql`:

- Creates `ai_response_audit`.
- Stores model, token, success, latency, and error information.

`005-create-refresh-tokens.sql`:

- Creates `refresh_tokens`.
- Stores token hashes, expiration, and revocation state.

`006-create-indexes.sql`:

- Adds indexes for common query paths like ticket customer, assigned agent, status, created time, and audit lookup.

### Why not Hibernate `ddl-auto: create` or `update`

Your `application.yaml` uses:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

This means Hibernate checks that entities match the database, but it does not create or modify schema.

Why this is important:

- Liquibase owns schema changes.
- Migrations are reviewable and repeatable.
- Production schema changes should be explicit.
- Hibernate `update` can make surprising changes and is not a migration history.

### What happens when the app starts

When Spring Boot starts:

1. It reads `spring.liquibase.change-log`.
2. Liquibase opens `classpath:db/changelog/master.xml`.
3. It reads included changesets in order.
4. It checks which changesets have already run.
5. It runs only new changesets.
6. Hibernate validates the schema because `ddl-auto` is `validate`.

### What happens if you edit an old changeset

Liquibase stores a checksum for each executed changeset.

If you change a changeset after it already ran, Liquibase detects that the checksum changed.

Result:

- Startup can fail with a checksum validation error.

Why:

- Old migrations are historical records.
- You should usually add a new changeset instead of editing one that already ran in a shared database.

## Topic 9 - `application.yml` Configuration

In this workspace, the main file is named `application.yaml`. Spring Boot supports both `.yml` and `.yaml`; they serve the same purpose.

### `spring.application`

```yaml
spring:
  application:
    name: support-api
```

This names the service `support-api`. The name can be used in logs, observability, service discovery patterns, and general app identification.

### `spring.profiles.active=local`

```yaml
spring:
  profiles:
    active: local
```

This activates the `local` profile.

Because `local` is active, Spring also loads:

```text
application-local.yml
```

That file overrides selected settings for local development.

### `spring.datasource`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/support_db
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

This configures PostgreSQL.

- `url`: where the database is.
- `username`: read from environment variable.
- `password`: read from environment variable.
- `driver-class-name`: PostgreSQL JDBC driver.

In Docker, `postgres` is the container hostname.

In `application-local.yml`, this is overridden:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/support_db
```

That is needed when Spring Boot runs directly on your machine instead of inside Docker.

### `spring.jpa`

```yaml
spring:
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

This configures JPA and Hibernate.

- `show-sql: false`: do not print every SQL statement to logs.
- `ddl-auto: validate`: check schema compatibility, but do not create or modify tables.
- `dialect`: tells Hibernate it is talking to PostgreSQL.

### `spring.data.redis`

```yaml
spring:
  data:
    redis:
      host: ${SPRING_REDIS_HOST}
      port: ${SPRING_REDIS_PORT}
```

This configures Redis.

Redis is planned as a cache/token store. The host and port come from environment variables in the main config.

In local profile:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

This is for running the app on your machine while Redis runs in Docker.

### `spring.kafka`

```yaml
spring:
  kafka:
    bootstrap-servers: kafka:29092
```

This tells Spring Kafka where the Kafka broker is.

The config also sets:

- consumer group ID: `support-api-group`
- offset reset: `earliest`
- string deserializers for consuming
- string serializers for producing

In local profile:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

That is the host-machine Kafka port.

### `spring.liquibase`

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/master.xml
```

This enables Liquibase and points it to the master changelog.

### `server`

```yaml
server:
  port: 8080
```

The support API listens on port `8080`.

### `management`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

This configures Spring Boot Actuator and Prometheus metrics.

- Exposes actuator endpoints.
- Enables the Prometheus endpoint.
- Enables Prometheus metrics export.

This supports observability for local Docker Compose monitoring.

### `logging`

```yaml
logging:
  level:
    root: INFO
    com.krish.supportapi: DEBUG
```

This sets log levels.

- Root logging is `INFO`.
- Your app package logs at `DEBUG`.

This keeps framework logs reasonable while allowing more detail from your code.

### `jwt`

```yaml
jwt:
  secret: ${JWT_SECRET}
  access-token-expiry-ms: ${JWT_ACCESS_EXPIRY_MS}
  refresh-token-expiry-ms: ${JWT_REFRESH_EXPIRY_MS}
```

This stores JWT configuration.

- Secret comes from an environment variable.
- Access token expiry comes from an environment variable.
- Refresh token expiry comes from an environment variable.

Never hardcode JWT secrets because anyone with the secret can potentially forge tokens.

### `openai`

```yaml
openai:
  api-key: ${OPENAI_API_KEY}
  model: gpt-4o-mini
  base-url: https://api.openai.com/v1
```

This stores OpenAI client configuration.

- API key is secret and comes from the environment.
- Model is `gpt-4o-mini`.
- Base URL is the OpenAI API root.

### `${ENV_VAR}` placeholders

Example:

```yaml
password: ${SPRING_DATASOURCE_PASSWORD}
```

This means Spring reads the value from the environment.

Why we use this:

- Secrets do not get committed to git.
- Different environments can use different credentials.
- Docker, CI, staging, and production can inject values safely.

### `application.yaml` vs `application-local.yml`

`application.yaml` contains default application configuration.

`application-local.yml` contains overrides for the local profile.

In your project, the main config uses Docker hostnames like:

- `postgres`
- `kafka`

The local config overrides them with:

- `localhost`

That is because container names are resolvable inside Docker networks, but not always from a Spring Boot process running directly on your host machine.

## Topic 10 - How Everything Connects

Here is the usual request lifecycle for this service once controllers and services are added.

Example: creating a ticket.

1. Client sends an HTTP request to the API.
2. Controller receives JSON.
3. Spring converts JSON into `CreateTicketRequest`.
4. Validation runs because the controller uses `@Valid`.
5. Controller calls a service method.
6. Service loads the current `User`.
7. Service builds a `Ticket` entity.
8. Service calls `TicketRepository.save(ticket)`.
9. JPA maps the `Ticket` object to the `public.tickets` table.
10. PostgreSQL stores the row.
11. Service maps the saved entity to `TicketResponse`.
12. Controller returns JSON to the client.

### Entity, repository, and database relationship

The relationship is:

```text
TicketRepository works with Ticket entity
Ticket entity maps to public.tickets table
public.tickets stores the actual rows
```

In code:

```java
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);
}
```

The repository method works with the Java field:

```java
private TicketStatus status;
```

Hibernate maps that to the database column:

```sql
status public.ticket_status
```

### How DTOs protect the entity layer

The API should not return entities directly.

Instead of returning `User`, return `UserResponse`.

Why:

- `User` contains `passwordHash`.
- `RefreshToken` contains `tokenHash`.
- Entities contain JPA relationships.
- Entities reflect database design, not necessarily API design.

DTOs give you a clean boundary.

Example mapping idea:

```java
UserResponse response = UserResponse.builder()
    .id(user.getId())
    .email(user.getEmail())
    .fullName(user.getFullName())
    .role(user.getRole())
    .isActive(user.isActive())
    .createdAt(user.getCreatedAt())
    .build();
```

### Layer diagram

```text
              HTTP request JSON
                     |
                     v
              Controller layer
          validates request DTOs
                     |
                     v
               Service layer
        business rules and transactions
                     |
                     v
             Repository layer
       Spring Data JPA proxy interfaces
                     |
                     v
                JPA/Hibernate
       maps entities to SQL operations
                     |
                     v
              PostgreSQL database
        tables created by Liquibase

Response path:

PostgreSQL -> Entity -> Service maps to Response DTO -> Controller -> JSON response
```

### Big picture

Your current foundation is strong because each major concern has a home:

- Liquibase owns database schema.
- Entities describe how Java maps to that schema.
- Enums keep allowed values consistent.
- DTOs define API input and output shapes.
- Repositories provide database access.
- Configuration wires the app to infrastructure.
- `UserDetails` prepares the user model for Spring Security authentication.

That is the base a Spring Boot microservice needs before adding controllers, services, security filters, JWT generation, Kafka producers/consumers, and OpenAI integration.
