# API — Development Guidelines

This file applies to all code inside `api/`. Rules here override any conflicting defaults.

## Stack

- **Java 21**, Spring Boot 3.3.4, Spring JDBC (no ORM), PostgreSQL 16, Redis
- **Build**: Maven — run `mvn verify` to compile, generate OpenAPI stubs, and run tests
- **Code generation**: OpenAPI Generator runs at `generate-sources`; never edit files under `target/generated-sources/`

## Lombok

Lombok is a required dependency for all new and modified Java code in this module.

### Maven dependency (must be present in `pom.xml`)

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.34</version>
    <scope>provided</scope>
</dependency>
```

### Annotation processor — add inside the `maven-compiler-plugin` configuration

```xml
<annotationProcessorPaths>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.34</version>
    </path>
</annotationProcessorPaths>
```

### Usage rules

- Use `@Data` on record-like classes that need getters, setters, `equals`, `hashCode`, and `toString`
- Use `@Value` for immutable value objects (all fields `final`, no setters)
- Use `@Builder` when constructing objects with more than 3 fields
- Use `@RequiredArgsConstructor` for constructor injection in Spring beans — never write constructors by hand
- Use `@Slf4j` for logging — never declare `Logger` fields manually
- Use `@UtilityClass` for static utility classes

## Nullability — No Ternary Operators

**Ternary operators are forbidden.** Use `Optional` or `@Nullable` instead.

### Rules

1. **Method may return null → return `Optional<T>`**

   ```java
   // WRONG
   public String getTitle() {
       return title != null ? title : "";
   }

   // RIGHT
   public Optional<String> getTitle() {
       return Optional.ofNullable(title);
   }
   ```

2. **Providing a default when value may be absent → use `Optional.ofNullable(...).orElse(...)`**

   ```java
   // WRONG
   String label = name != null ? name : "unknown";

   // RIGHT
   String label = Optional.ofNullable(name).orElse("unknown");
   ```

3. **Nullable parameter or field → annotate with `@lombok.NonNull` (throws) or `@org.springframework.lang.Nullable` (documents intent)**

   ```java
   // Fields that may legitimately be null
   @Nullable private String description;

   // Parameters that must never be null — Lombok generates the null-check
   public void process(@NonNull String slug) { ... }
   ```

4. **Chained transformations on a value that may be absent → use `Optional.map` / `Optional.flatMap`**

   ```java
   // WRONG
   String upper = value != null ? value.toUpperCase() : null;

   // RIGHT
   Optional<String> upper = Optional.ofNullable(value).map(String::toUpperCase);
   ```

5. **Conditional execution → use `Optional.ifPresent` or `Optional.ifPresentOrElse`**

   ```java
   // WRONG
   if (result != null) save(result);

   // RIGHT
   Optional.ofNullable(result).ifPresent(this::save);
   ```

## Architecture Conventions

- **Controllers** only delegate — no business logic, no null-checks, no branching
- **Services** own all business logic; they return `Optional<T>` or throw typed exceptions
- **Repository** returns `Optional<T>` for single-row lookups, `List<T>` (never `null`) for multi-row
- **`IndexerService`**: full reindex via `reindex()`, single-meme upsert via `indexSingle(MemeIndexRequest)`
- All Redis cache invalidation goes through `invalidateCaches()` in `IndexerService`
- All admin endpoints are protected by `ApiKeyAuthFilter` — never bypass it

## OpenAPI Contract

- The source of truth is `src/main/resources/openapi.yaml`
- Every new endpoint or request/response field must be defined there first
- Run `mvn generate-sources` after editing the spec to regenerate stubs
- Delegate implementations live in `src/main/java/com/memes/api/controller/`

## Logging

Use `@Slf4j` (Lombok). Log at:
- `INFO` — indexed counts, cache invalidations
- `DEBUG` — individual record details, SQL parameters
- `WARN` — recoverable errors (parse failures, cache misses)
- `ERROR` — unrecoverable failures only
