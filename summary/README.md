# Customer Summary service

Standalone Spring MVC application in package `com.example.summary`, port 8070.
Java 17 source target, Spring Boot 3.5.6, Spring Cloud 2025.0.0, OpenFeign.
The DTOs are Summary-owned Java records, following the record style used by Gateway.
There is no database, persistence dependency, service discovery, circuit breaker, or fallback.
All three downstream calls are sequential.

## Request flow

1. Client logs in using `POST http://localhost:7000/auth/login`.
2. Client sends `GET http://localhost:7000/summary/api/customer?mobileNumber=9876543210`
   with `Authorization: Bearer <accessToken>`.
3. Gateway validates the JWT before forwarding the original path/query to Summary on 8070.
4. Summary controller requires exactly 10 ASCII digits.
5. Summary service calls Accounts first: `GET /accounts/api/fetch?mobileNumber=...` on 8080.
6. If Accounts succeeds, call Cards: `GET /api/fetch?mobileNumber=...` on 9000.
7. If Cards succeeds or returns 404, call Loans: `GET /api/fetch?mobileNumber=...` on 8090.
8. Map the Accounts response's name/email/mobileNumber to `customer`, its `accountsDto`
   to `account`, and the two product responses to `card` and `loan`.
9. Return one JSON response through Gateway.

Accounts, Cards and Loans use their existing independent PostgreSQL databases.
Summary calls the services directly, using their DTO APIs. JWT validation stays at Gateway;
direct service ports are intended for trusted infrastructure. Existing authentication does
not establish ownership of the queried mobile number.

## Configuration

| Variable | Default | Process |
|---|---|---|
| SUMMARY_PORT | 8070 | Summary |
| ACCOUNTS_SERVICE_URL | http://localhost:8080 | Summary and Gateway |
| CARDS_SERVICE_URL | http://localhost:9000 | Summary and Gateway |
| LOANS_SERVICE_URL | http://localhost:8090 | Summary and Gateway |
| FEIGN_CONNECT_TIMEOUT | 2000 milliseconds | Summary |
| FEIGN_READ_TIMEOUT | 5000 milliseconds | Summary |
| SUMMARY_SERVICE_URL | http://localhost:8070 | Gateway |

URLs are base URLs without API paths. If you change SUMMARY_PORT, also update Gateway's
SUMMARY_SERVICE_URL. Feign uses Spring Cloud's default no-retry behavior.

Gateway route 3 preserves `/summary/api/**` without stripping a prefix.
Routes 0, 1 and 2 retain their original definitions.
Both Gateway's JWT filter matcher and authenticated-path matcher include Summary.

## Response and errors

Illustrative successful response:

```json
{
  "customer": {
    "name": "Summary Customer",
    "email": "summary@example.test",
    "mobileNumber": "9876543210"
  },
  "account": {
    "accountNumber": 3454433243,
    "accountType": "Savings",
    "branchAddress": "Main Branch"
  },
  "card": {
    "mobileNumber": "9876543210",
    "cardNumber": "100646930341",
    "cardType": "Credit Card",
    "totalLimit": 100000,
    "amountUsed": 0,
    "availableAmount": 100000
  },
  "loan": {
    "mobileNumber": "9876543210",
    "loanNumber": "548732457654",
    "loanType": "Home Loan",
    "totalLoan": 100000,
    "amountPaid": 0,
    "outstandingAmount": 100000
  }
}
```

| Condition | HTTP status / behavior |
|---|---|
| Missing or invalid bearer token at Gateway | 401, request never reaches Summary |
| Missing/blank/non-10-digit mobile number | 400, no downstream calls |
| Accounts returns 404 for customer or account | 404, no product calls |
| Cards returns 404 | 200 with explicit `"card": null` |
| Loans returns 404 | 200 with explicit `"loan": null` |
| Both products return 404 | 200 with both fields explicitly null |
| Downstream timeout | 504 |
| Downstream connection failure | 503 |
| Other downstream HTTP errors, invalid JSON, or empty success body | 502 |
| Incomplete customer/account success response | 502 |
| Unexpected Summary error | 500 |

A 404 from Accounts is the existing API's signal for missing customer/account data.
An invalid success payload is a downstream contract failure.
Summary errors consistently contain `apiPath`, `errorCode`, `errorMessage`, and
`errorTime`. Gateway authentication errors retain their existing JSON format.
Raw downstream error bodies are not returned to clients.

## Exact IntelliJ run steps

1. Open the existing BankFeatures project.
2. Right-click `summary/pom.xml` and choose **Add as Maven Project**.
   If that action is absent, use the Maven tool window's **Link Maven Projects** (+)
   and select that POM. Reload Maven projects.
3. In **File > Project Structure**, use an installed JDK 17 or newer.
   The build was verified with JDK 24.0.1 and compiles for Java 17.
4. Start PostgreSQL. Keep the existing connection configuration for `accounts_db`,
   `cards_db`, and `loans_db`.
5. Run these main classes with their own module classpaths:
   - Accounts: `com.example.accounts.AccountsApplication` (8080)
   - Cards: `com.example.cards.CardsApplication` (9000)
   - Loans: `com.example.loans.LoansApplication` (8090)
6. Open `summary/src/main/java/com/example/summary/SummaryApplication.java`.
   Click the gutter Run icon beside `main`, then **Run SummaryApplication**.
   This creates a run configuration with the Summary module classpath.
7. In **Run > Edit Configurations > SummaryApplication**, use working directory
   `C:\Users\stuti\IdeaProjects\BankFeatures\summary`.
   Local defaults require no environment variables. For explicit configuration,
   open the environment-variable editor and add:
   - `SUMMARY_PORT=8070`
   - `ACCOUNTS_SERVICE_URL=http://localhost:8080`
   - `CARDS_SERVICE_URL=http://localhost:9000`
   - `LOANS_SERVICE_URL=http://localhost:8090`
8. In the existing Gateway run configuration, preserve all six required authentication
   environment variables: `AUTH_USERNAME`, `AUTH_PASSWORD_HASH`, `JWT_SECRET`,
   `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_TTL_SECONDS`.
   Add `SUMMARY_SERVICE_URL=http://localhost:8070` if you want an explicit value.
   Use the environment-variable editor so dollar signs in the BCrypt hash are preserved.
   For initial credential/key creation, follow [Gateway authentication setup](../Gateway/AUTHENTICATION.md#required-environment-variables).
9. Restart `com.example.Gateway.GatewayApplication` (7000) to load the new route
   and JWT matchers. Stop an older instance before restarting on the same port.
10. Confirm the consoles show Accounts 8080, Cards 9000, Loans 8090, Summary 8070,
    and Gateway 7000. Test through Gateway using the steps below.

Summary can start without PostgreSQL or running backends; a successful summary request
requires Accounts and both product services to be reachable, even when products are absent.

## Exact Postman steps

### Login

1. Create/select a Postman environment with:
   - `baseUrl` = `http://localhost:7000`
   - `username` = Gateway's configured AUTH_USERNAME
   - `password` = the plaintext password corresponding to AUTH_PASSWORD_HASH
   - `accessToken` = empty
   - `mobileNumber` = a 10-digit number belonging to a customer, or a fresh test number
2. Create **POST** `{{baseUrl}}/auth/login`.
   Set **Authorization > No Auth**, **Body > raw > JSON**:
   ```json
   {"username":"{{username}}","password":"{{password}}"}
   ```
3. Send. Expect 200. Copy `accessToken` from the response into the environment,
   or use this **Scripts > Post-response** script:
   ```javascript
   pm.test("Login succeeded", () => pm.response.to.have.status(200));
   if (pm.response.code === 200) {
     pm.environment.set("accessToken", pm.response.json().accessToken);
   } else {
     pm.environment.unset("accessToken");
   }
   ```
4. For every subsequent banking request, choose **Authorization > Bearer Token**
   and enter `{{accessToken}}`. Keep credentials and tokens in local secret values.

### Prepare data and test all missing-product cases

Use a fresh 10-digit `mobileNumber` so these steps do not alter existing records.

1. Create **POST** `{{baseUrl}}/accounts/api/create`, **Body > raw > JSON**:
   ```json
   {"name":"Summary Customer","email":"summary@example.test","mobileNumber":"{{mobileNumber}}"}
   ```
   Send with the bearer token. Expect 201.
2. Create **GET** `{{baseUrl}}/summary/api/customer`.
   In **Params**, add key `mobileNumber`, value `{{mobileNumber}}`.
   Send with the bearer token. Expect 200, populated customer/account, card null, loan null.
3. Create **POST** `{{baseUrl}}/cards/api/create`, with the same query parameter
   and bearer token, no request body. Expect 201.
4. Repeat Summary GET. Expect card populated, loan null.
5. Create **POST** `{{baseUrl}}/loans/api/create`, with the same query parameter
   and bearer token, no request body. Expect 201.
6. Repeat Summary GET. Expect all four sections populated.
7. To check card-only absence without deleting data, use a second fresh 10-digit
   number, create its Account and Loan only, then request its Summary.
   Expect card null and loan populated.

### Negative cases

- Remove the bearer token: Summary GET must return 401.
- Use `Bearer not-a-jwt`: 401.
- Use a valid token and mobileNumber `123`, an empty value, or omit the parameter: 400.
- Use a fresh number with no Accounts record: 404.
- Stop Cards temporarily and request a known customer's Summary: 503 for connection refusal,
  or 504 if the connection stalls. It must not return 200 with card null. Restart Cards.
- Automated tests below cover downstream HTTP 500 and malformed/empty downstream responses.
- If a token expires, repeat login before continuing.

Optional cleanup: for numbers created solely for this test, send DELETE to
`/cards/api/delete`, `/loans/api/delete`, and finally `/accounts/api/delete`,
each through Gateway with the corresponding mobileNumber and bearer token.
Only delete records you created for this exercise.

## Build and verification

From the repository root in PowerShell:

```powershell
Push-Location summary
.\mvnw.cmd -B -ntp verify
Pop-Location
Push-Location Gateway
.\mvnw.cmd -B -ntp verify
Pop-Location
```

`verify` runs tests, packages the executable JAR, and completes Maven verification.
No additional standalone `test` invocation is necessary.

Verified on 2026-09-15 with Java 24.0.1 and Maven 3.9.11:

- Summary: 32 tests, 0 failures/errors/skips; BUILD SUCCESS.
- Gateway: 15 tests, 0 failures/errors/skips; BUILD SUCCESS.
- Executable JARs: `summary/target/summary-0.0.1-SNAPSHOT.jar` and
  `Gateway/target/Gateway-0.0.1-SNAPSHOT.jar`.
- Summary integration tests use the real application/controller/service/Feign clients and
  local JDK HTTP stubs. They assert JSON mapping, validation, optional 404s, Accounts 404,
  exact HTTP methods/paths/queries, call order, HTTP errors, decoding failures, and timeout.
- A focused service test verifies connection-failure translation and stopping aggregation.
- Gateway tests preserve existing route checks and extend existing token rejection tests
  to Summary, including ensuring rejected requests never reach the stub backends.
- Accounts/Cards/Loans source files were not changed. Their database-backed tests were not
  rerun for this change. No live database records were created or modified.
- These tests verify the two application boundaries separately; a live five-service
  Postman exercise was not run.