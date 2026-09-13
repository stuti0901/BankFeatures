BankFeatures build and runtime verification — 2026-09-13

All four modules built successfully and ran simultaneously with PostgreSQL 17. Final module suites: 10 tests, zero failures/errors/skips. The eight database tests also passed against fresh temporary schemas. Real HTTP CRUD smoke tests passed through Gateway for all three services. No commit, push, or architecture additions were performed.

| Module | Directory | Port | Direct API | Gateway API | Tests |
|---|---|---:|---|---|---:|
| Accounts | accounts/accounts | 8080 | /accounts/api | /accounts/api | 2 |
| Cards | cards/cards | 9000 | /api | /cards/api | 3 |
| Loans | loans/loans | 8090 | /api | /loans/api | 3 |
| Gateway | Gateway | 7000 | /dashboard | — | 2 |

The complete application source, POMs, resource files, tests, wrapper configuration, and project configuration were inspected before editing. The project-local AGENTS.md search found none. An earlier parent-directory search was interrupted; after the user's correction, all AGENTS.md searches were restricted to BankFeatures.

Every changed or added file is listed below. Paths are relative to this project.

| File | Change |
|---|---|
| [.idea/compiler.xml](.idea/compiler.xml) | IntelliJ automatically refreshed the annotation processor profile to Lombok 1.18.38; not manually edited. |
| [Gateway/src/main/java/com/example/Gateway/controller/HomeController.java](Gateway/src/main/java/com/example/Gateway/controller/HomeController.java) | Made dashboard mapping a top-level controller method and supplied the selected service to the template. |
| [Gateway/src/main/resources/application.properties](Gateway/src/main/resources/application.properties) | Distinct Accounts/Cards/Loans paths; StripPrefix for Cards/Loans; configurable backend URLs. |
| [Gateway/src/main/resources/templates/dashboard.html](Gateway/src/main/resources/templates/dashboard.html) | Service buttons now select the corresponding dashboard section instead of linking to unmapped /api. |
| [Gateway/src/test/java/com/example/Gateway/GatewayApplicationTests.java](Gateway/src/test/java/com/example/Gateway/GatewayApplicationTests.java) | Actual HTTP forwarding tests with three local stub backends, query/path assertions, and dashboard rendering. |
| [accounts/accounts/pom.xml](accounts/accounts/pom.xml) | Lombok 1.18.38 and explicit Maven annotation processor configuration for JDK 24. |
| [accounts/accounts/src/main/java/com/example/accounts/AccountsApplication.java](accounts/accounts/src/main/java/com/example/accounts/AccountsApplication.java) | Enabled existing JPA auditing. |
| [accounts/accounts/src/main/java/com/example/accounts/dto/AccountsDto.java](accounts/accounts/src/main/java/com/example/accounts/dto/AccountsDto.java) | Replaced unsupported String validators on Long with numeric/null constraints. |
| [accounts/accounts/src/main/java/com/example/accounts/dto/CustomerDto.java](accounts/accounts/src/main/java/com/example/accounts/dto/CustomerDto.java) | Enabled validation of nested account details. |
| [accounts/accounts/src/main/resources/application.properties](accounts/accounts/src/main/resources/application.properties) | Explicit port 8080, application name, master changelog, Hibernate validate, and SQL initialization disabled. |
| [accounts/accounts/src/main/resources/schema.sql](accounts/accounts/src/main/resources/schema.sql) | PostgreSQL identity/BIGINT/TIMESTAMP schema for customer and accounts; account number remains application-assigned. |
| [accounts/accounts/src/main/resources/db/changelog/db-changelog-master.xml](accounts/accounts/src/main/resources/db/changelog/db-changelog-master.xml) | Added correct schema initialization, missing audit backfill, and customer sequence synchronization. Excludes the erroneous historical cards changelog. |
| [accounts/accounts/src/test/java/com/example/accounts/AccountsApplicationTests.java](accounts/accounts/src/test/java/com/example/accounts/AccountsApplicationTests.java) | Health and transactional CRUD/auditing regression tests. |
| [cards/cards/pom.xml](cards/cards/pom.xml) | Trimmed artifact/name whitespace; Lombok 1.18.38 and explicit annotation processor configuration. |
| [cards/cards/src/main/java/com/example/cards/dto/CardsDto.java](cards/cards/src/main/java/com/example/cards/dto/CardsDto.java) | Accepts both generated 12-digit and existing seeded 16-digit card numbers. |
| [cards/cards/src/main/resources/application.properties](cards/cards/src/main/resources/application.properties) | Explicit port 9000, application name, master changelog, Hibernate validate, and SQL initialization disabled. |
| [cards/cards/src/main/resources/schema.sql](cards/cards/src/main/resources/schema.sql) | PostgreSQL identity/BIGINT/TIMESTAMP schema with audit defaults for original seed inserts. |
| [cards/cards/src/main/resources/db/changelog/db-changelog-master.xml](cards/cards/src/main/resources/db/changelog/db-changelog-master.xml) | Creates table before original seed changeset; backfills missing audit fields. |
| [cards/cards/src/test/java/com/example/cards/CardsApplicationTests.java](cards/cards/src/test/java/com/example/cards/CardsApplicationTests.java) | Added health, transactional CRUD/auditing, and existing seed update tests. |
| [loans/loans/pom.xml](loans/loans/pom.xml) | Lombok 1.18.38 and explicit annotation processor configuration. |
| [loans/loans/src/main/java/com/example/loans/LoansApplication.java](loans/loans/src/main/java/com/example/loans/LoansApplication.java) | Enabled existing JPA auditing. |
| [loans/loans/src/main/java/com/example/loans/dto/LoansDto.java](loans/loans/src/main/java/com/example/loans/dto/LoansDto.java) | Accepts generated 12-digit numbers and existing legacy LN identifiers. |
| [loans/loans/src/main/resources/application.properties](loans/loans/src/main/resources/application.properties) | Retains port 8090; application name, master changelog, Hibernate validate, and SQL initialization disabled. |
| [loans/loans/src/main/resources/schema.sql](loans/loans/src/main/resources/schema.sql) | Converted standalone reference SQL to PostgreSQL identity/BIGINT/TIMESTAMP syntax. |
| [loans/loans/src/main/resources/db/changelog/db-changelog-master.xml](loans/loans/src/main/resources/db/changelog/db-changelog-master.xml) | Retains original migrations, advances identity sequence beyond explicit seed IDs, and backfills missing audit fields. |
| [loans/loans/src/test/java/com/example/loans/LoansApplicationTests.java](loans/loans/src/test/java/com/example/loans/LoansApplicationTests.java) | Added health, transactional CRUD/auditing, and existing seed update tests. |
| [BUILD-VERIFICATION.md](BUILD-VERIFICATION.md) | This change inventory, commands, results, and prerequisites. |

Gateway's existing Spring Boot 3.5.6 / Spring Cloud 2025.0.0 pairing and spring-cloud-starter-gateway-server-webflux dependency resolved and passed tests, so its POM was preserved. The existing spring.cloud.gateway.server.webflux prefix is correct for that release. See [Spring Cloud release notes](https://spring.io/blog/2025/05/29/spring-cloud-2025-0-0-is-abvailable/). Lombok 1.18.38 adds JDK 24 support; see [Lombok changelog](https://projectlombok.org/changelog).

Liquibase uses db/changelog/db-changelog-master.xml in each database module. Existing changelog files were not rewritten or deleted. Accounts' old changelog incorrectly describes cards, and even differs from its recorded database history; it is retained as inactive historical reference. The new master creates missing customer/accounts tables without dropping existing ones. Cards creates its table before including the original inserts. Loans retains its original table/seed migrations and adds sequence/audit repair. Existing DATABASECHANGELOG rows remain. Hibernate validates schema instead of changing it, and spring.sql.init.mode=never prevents a second schema initializer. The Loans schema.sql remains a standalone reference script; its original Liquibase changesets remain the runtime schema source.

Existing customer/card/loan identifiers and business fields were retained. Corrective migrations filled only missing created_at/created_by values (migration time/system) and advanced customer/loan sequences safely. SQL tests use transactions that roll back test changes; sequences can still advance. HTTP smoke tests committed temporary records and deleted only those records. The uniquely named temporary test schema bankfeatures_verify_1789309045722 was created in each database and removed after successful fresh-schema tests. No application schema or important project file was deleted. Maven clean removed only generated target output.

Commands executed and results:

- Read-only inspection: rg --files, project-local rg --files --hidden -g AGENTS.md, Get-Content, Get-ChildItem, git status --short, git diff, java -version, wrapper -version, Get-Service, Get-NetTCPConnection, and psql metadata/history/identity queries.
- java -version: Oracle JDK 24.0.1. mvn -version: Maven is not on PATH. Project wrappers worked: Maven 3.9.9 for Accounts/Cards/Loans and 3.9.11 for Gateway.
- .\\mvnw.cmd -B -ntp verify in all four module directories. Initial Accounts compile failed because Lombok processing was absent. Final verify passed in all modules, including packaging executable JARs.
- .\\mvnw.cmd -B -ntp clean verify in every module. These clean runs exposed the stale Accounts sequence and a Gateway test-property override issue; both were corrected and subsequent verify runs passed. Cards/Loans clean builds passed. No tests were skipped to produce the final packages.
- Fresh-schema tests ran the command below for each database module, with its module/database name and the temporary schema substituted. Accounts: 2 passed; Cards: 3 passed; Loans: 3 passed.

```powershell
.\\mvnw.cmd -B -ntp test '-Dspring.datasource.url=jdbc:postgresql://localhost:5432/<module>_db?currentSchema=<temporary-schema>' '-Dspring.liquibase.default-schema=<temporary-schema>' '-Dspring.jpa.properties.hibernate.default_schema=<temporary-schema>'
```

- Started java -jar target/<module>-0.0.1-SNAPSHOT.jar from each module directory using hidden Start-Process, with stdout/stderr redirected to target/smoke-startup.log and target/smoke-startup-error.log. Gateway JAR name is Gateway-0.0.1-SNAPSHOT.jar.
- Invoke-WebRequest / Invoke-RestMethod: GET /actuator/health on 8080, 9000, 8090 returned 200/UP; Gateway /dashboard?service=cards returned 200; existing card and loan fetches through Gateway returned 200.
- For each service, real Gateway POST create returned 201; Gateway/direct GET fetch returned 200 with equal bodies; PUT update returned 200 and the direct endpoint confirmed persisted changes; DELETE returned 200; subsequent fetch returned 404. A random unused test mobile number was checked before creation and cleaned afterward.
- Gateway regression tests forward requests to three independent local HTTP stub servers and check service selection, query strings, Accounts path preservation, Cards/Loans prefix removal, and 404 for ambiguous /api/fetch. The dashboard test checks rendered service selection.
- git diff --check passed. Final startup logs contained no ERROR entries. Git emitted only its usual LF/CRLF conversion notices.

To rebuild, run .\\mvnw.cmd -B -ntp verify from each directory in the table. To run in separate terminals from the project root:

```powershell
java -jar accounts/accounts/target/accounts-0.0.1-SNAPSHOT.jar
java -jar cards/cards/target/cards-0.0.1-SNAPSHOT.jar
java -jar loans/loans/target/loans-0.0.1-SNAPSHOT.jar
java -jar Gateway/target/Gateway-0.0.1-SNAPSHOT.jar
```

At verification completion the services were left running: Accounts PID 55748, Cards PID 22392, Loans PID 58860, Gateway PID 57860. Check these process IDs still identify the same Java applications before stopping them. Logs and Surefire reports are under each module's target directory; the latest database Surefire reports describe the fresh-schema run.

Remaining prerequisites and limits: PostgreSQL must be reachable with the configured accounts_db, cards_db, and loans_db databases and credentials; database regression tests require it too. Liquibase needs schema-migration privileges. Spring's standard SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, and SPRING_DATASOURCE_PASSWORD environment variables can override local settings. Use each wrapper when Maven is not installed; first dependency resolution needs network access. This run used JDK 24.0.1 and emits non-fatal third-party native-access/Unsafe/agent warnings. No remaining failures were observed in the tested builds, startup, migrations, or CRUD flows. Existing login.html is still an unwired template, and dashboard descriptions remain placeholders; authentication and new banking features were outside this task. Gateway has no Actuator dependency, so its availability was verified using dashboard and routing HTTP tests.
