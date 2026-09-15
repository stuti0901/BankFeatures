# Gateway JWT authentication

The Gateway uses reactive Spring Security and JJWT (HS256). It authenticates a configured user with BCrypt and validates bearer tokens before forwarding banking API requests. All existing route definitions and all three backend modules are unchanged.

## Build and tests

From the Gateway directory, with JDK 17 or newer:

```powershell
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp verify
```

Tests create random usernames, passwords, signing keys, issuer and audience values at runtime. They use local HTTP stub backends and do not require PostgreSQL or running banking services. They exercise successful and failed login, public pages/health, missing/malformed/tampered/expired tokens, invalid claims, duplicate headers, rejection before forwarding, statelessness, original routing and configuration validation.

## Required environment variables

All six variables must be provided to the Gateway process. Empty or invalid configuration fails startup.

| Variable | Value |
|---|---|
| AUTH_USERNAME | Your chosen login username |
| AUTH_PASSWORD_HASH | Raw BCrypt hash of your password, starting with $2a$, $2b$ or $2y$; do not add a {bcrypt} prefix |
| JWT_SECRET | Base64 encoding of at least 32 cryptographically random bytes |
| JWT_ISSUER | Your chosen issuer identifier |
| JWT_AUDIENCE | Your chosen API audience identifier |
| JWT_TTL_SECONDS | Positive integer access-token lifetime in seconds |

Use a password of at most 72 UTF-8 bytes, the BCrypt limit. These are application credentials, not the PostgreSQL credentials. No defaults or real credentials are committed.

### PowerShell setup

Run the following in the Gateway directory. The username and password are entered locally; the script computes the hash without putting the password in command history or writing it to a file. JDK tools java and jshell must be on PATH.

First prepare the dependency classpath:

```powershell
.\mvnw.cmd -B -ntp dependency:build-classpath '-Dmdep.outputFile=target/auth-classpath.txt' '-Dmdep.includeScope=runtime'
```

Then configure the current PowerShell process:

```powershell
$env:AUTH_USERNAME = Read-Host 'Login username'
$localPassword = Read-Host 'Login password (at most 72 UTF-8 bytes)' -AsSecureString
$env:BANKFEATURES_PASSWORD_INPUT = [Net.NetworkCredential]::new('', $localPassword).Password
try {
    if ([Text.Encoding]::UTF8.GetByteCount($env:BANKFEATURES_PASSWORD_INPUT) -gt 72) {
        throw 'Password exceeds the BCrypt 72-byte limit'
    }
    $authClasspath = (Get-Content -Raw target/auth-classpath.txt).Trim()
    $hashScript = 'System.out.println("BCRYPT_RESULT=" + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(System.getenv("BANKFEATURES_PASSWORD_INPUT")));' + "`n/exit"
    $hashOutput = $hashScript | jshell --class-path $authClasspath -
    $hashMatch = [regex]::Match(($hashOutput -join "`n"), 'BCRYPT_RESULT=(\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53})')
    if (-not $hashMatch.Success) { throw 'BCrypt hash generation failed' }
    $env:AUTH_PASSWORD_HASH = $hashMatch.Groups[1].Value
} finally {
    Remove-Item Env:BANKFEATURES_PASSWORD_INPUT -ErrorAction SilentlyContinue
    $localPassword = $null
    $hashOutput = $null
}
$jwtBytes = New-Object byte[] 32
$jwtRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $jwtRandom.GetBytes($jwtBytes) } finally { $jwtRandom.Dispose() }
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:JWT_ISSUER = 'bankfeatures-local'
$env:JWT_AUDIENCE = 'bankfeatures-api'
$env:JWT_TTL_SECONDS = '900'

java -jar target/Gateway-0.0.1-SNAPSHOT.jar
```

Issuer, audience and lifetime above are example local configuration choices. Keep the same JWT_SECRET across restarts to preserve unexpired tokens; rerunning the random-key setup invalidates existing tokens. If pasting a BCrypt hash directly into PowerShell, use single quotes so its dollar signs are preserved.

Environment variables belong to this PowerShell process and its children. For IntelliJ, configure these six variables in the Gateway run configuration instead. Restart an already running Gateway to load this implementation and configuration; only one Gateway can use port 7000.

Start Accounts (8080), Cards (9000) and Loans (8090) separately with their existing database configuration. The Gateway's existing optional ACCOUNTS_SERVICE_URL, CARDS_SERVICE_URL and LOANS_SERVICE_URL overrides still work.

## Postman

1. Create environment variables baseUrl = http://localhost:7000, username = the configured username, password = the plaintext password you entered locally, and accessToken (initially empty). Keep password and accessToken local/secret.
2. Create POST {{baseUrl}}/auth/login. Select Authorization > No Auth and Body > raw > JSON:
   ```json
   {
     "username": "{{username}}",
     "password": "{{password}}"
   }
   ```
   Send with Content-Type: application/json. Expect 200 and:
   ```json
   {
     "accessToken": "<signed JWT>",
     "tokenType": "Bearer",
     "expiresIn": 900
   }
   ```
3. In the login request's post-response script, save the token only after a successful login:
   ```javascript
   pm.test("Login succeeded", () => pm.response.to.have.status(200));
   if (pm.response.code === 200) {
       pm.environment.set("accessToken", pm.response.json().accessToken);
   } else {
       pm.environment.unset("accessToken");
   }
   ```
4. Create GET requests to each URL below, replacing mobileNumber with an existing record. Select Authorization > Bearer Token and enter {{accessToken}}:
   - {{baseUrl}}/accounts/api/fetch?mobileNumber=<existing-mobile-number>
   - {{baseUrl}}/cards/api/fetch?mobileNumber=<existing-mobile-number>
   - {{baseUrl}}/loans/api/fetch?mobileNumber=<existing-mobile-number>
   A valid token lets the original service response through: normally 200 for an existing record, or a business 404 for a missing record. The service must be running for successful forwarding.
5. Select No Auth and remove any manually entered Authorization header: all three banking requests must return 401. Basic authentication also returns 401.
6. Try Bearer not-a-jwt, or change the first character of a valid token's signature (the segment after the second dot): expect 401.
7. Change the login password to a wrong value: expect 401.
8. For expiry testing, set JWT_TTL_SECONDS to 5, restart the Gateway with the same key, log in again, then wait more than five seconds and send that newly issued token: expect 401. Changing TTL does not shorten tokens already issued.
9. With No Auth, GET /, /login and /actuator/health remain accessible. Gateway health returns 200 with status UP under normal conditions.

Authentication errors return JSON with status 401 and error Unauthorized, with WWW-Authenticate: Bearer. Malformed JSON or missing login fields return 400.

## Authentication flow and scope

POST /auth/login checks the configured username and BCrypt hash using Spring Security on a bounded-elastic scheduler. Success returns an HS256 JWT containing sub, iss, aud, iat and exp. Login responses are not cached.

For /accounts/api/**, /cards/api/** and /loans/api/**, AuthenticationWebFilter extracts one Authorization: Bearer header, verifies the signature, allowed algorithm, issuer, audience, subject, expiration and any not-before claim, and establishes authentication for that request. Missing or invalid tokens stop at the Gateway with 401.

Accounts paths are preserved. Cards and Loans keep StripPrefix=1. No route predicates, URIs or filters are changed.

Security contexts and saved requests are not stored in sessions. Form login, HTTP Basic, cookie authentication and server logout are disabled. CSRF is disabled for this header-based authentication. Clients must send a token on every banking request. There is no refresh token or server-side token revocation; sign out by discarding the token, which remains valid until expiry.

The existing login page now submits JSON and holds the token only in page memory; reloading or navigating away clears it. The placeholder dashboard remains public and contains no banking data or API calls. Other unmatched paths retain their existing behavior; additional Actuator paths are denied, and only health is exposed.

This protects requests through port 7000. Direct backend ports still have their existing behavior and should be private to trusted infrastructure. Authentication does not add customer-specific ownership or role authorization. Use HTTPS outside local development.

Implementation references: [Spring Security WebFlux](https://docs.spring.io/spring-security/reference/6.5/reactive/configuration/webflux.html), [JJWT](https://github.com/jwtk/jjwt).

## Customer Summary API

The Summary service is protected by the same JWT validation as Accounts, Cards and Loans.
Both the JWT authentication filter matcher and authenticated-path matcher include
`/summary/api/**`. Missing or invalid tokens return 401 before forwarding.

Route 3 forwards `/summary/api/**` unchanged to
`${SUMMARY_SERVICE_URL:http://localhost:8070}`. Routes 0 through 2 are unchanged.

Start `com.example.summary.SummaryApplication`, then call
`GET http://localhost:7000/summary/api/customer?mobileNumber=9876543210`
with `Authorization: Bearer <accessToken>`.

See [Summary run and Postman instructions](../summary/README.md).
