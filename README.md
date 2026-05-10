# BOLA Security Issue Demo

A Spring Boot project demonstrating the BOLA (Broken Object Level Authorization) vulnerability and its fix.

## Overview

BOLA occurs when an application exposes endpoints that handle object identifiers without proper authorization checks, allowing attackers to access unauthorized data by modifying the object ID in requests.

## Endpoints

### User Endpoints (In-Memory)
| Endpoint | Method | Description | Authorization |
|----------|--------|-------------|---------------|
| `/api/users/bola-issue?userId={id}` | GET | Vulnerable endpoint - no authorization check | Authenticated |
| `/api/users/bola-fix?userId={id}` | GET | Fixed endpoint - validates ownership via SecurityService | Authenticated |

### Account Endpoints (JPA/H2 Database)
| Endpoint | Method | Description | Authorization |
|----------|--------|-------------|---------------|
| `/api/accounts/bola-issue/{id}` | GET | Vulnerable endpoint - no authorization check | Authenticated |
| `/api/accounts/bola-fix/{id}` | GET | Fixed endpoint - validates ownership via database query | Authenticated |

## Prerequisites

- Java 17+
- Maven 3.6+

## How to Run

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

H2 Console available at: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:boladb`
- Username: `sa`
- Password: (empty)

## How to Test BOLA Issues

### Test Users

| Username | Password | Role | User ID |
|----------|----------|------|---------|
| user1 | password | USER | 1 |
| user2 | password | USER | 2 |

### Sample Account Data

| ID | Account Name | Account Number | Owner | Balance |
|----|--------------|----------------|-------|---------|
| 1 | Savings Account | ACC-001 | user1 | 5000.00 |
| 2 | Checking Account | ACC-002 | user1 | 1500.00 |
| 3 | Savings Account | ACC-003 | user2 | 10000.00 |
| 4 | Investment Account | ACC-004 | user2 | 25000.00 |

---

## Testing User Endpoints

### Test the VULNERABLE endpoint (`/api/users/bola-issue`)

Access another user's data while authenticated as user1 (BOLA vulnerability):
```bash
curl -u user1:password "http://localhost:8080/api/users/bola-issue?userId=2"
```
Result: **VULNERABLE** - Shows "loggedInAs": "user1" but returns Bob's data.

### Test the FIXED endpoint (`/api/users/bola-fix`)

Access own data (allowed):
```bash
curl -u user1:password "http://localhost:8080/api/users/bola-fix?userId=1"
```

Access another user's data (blocked):
```bash
curl -u user1:password "http://localhost:8080/api/users/bola-fix?userId=2"
```
Expected: **404 Not Found**

---

## Testing Account Endpoints (JPA/H2 Database)

### Test the VULNERABLE endpoint (`/api/accounts/bola-issue/{id}`)

Access another user's account while authenticated as user1:
```bash
curl -u user1:password "http://localhost:8080/api/accounts/bola-issue/3"
```
Result: **VULNERABLE** - Shows "loggedInAs": "user1" but returns account ACC-003 (owned by user2).

### Test the FIXED endpoint (`/api/accounts/bola-fix/{id}`)

Access own account (allowed):
```bash
curl -u user1:password "http://localhost:8080/api/accounts/bola-fix/1"
```
Expected: Returns account ACC-001.

Access another user's account (blocked):
```bash
curl -u user1:password "http://localhost:8080/api/accounts/bola-fix/3" --write-out "%{http_code}"
```
Expected: **404 Not Found**

---

## Key Code Differences

### Vulnerable Code (bola-issue)
```java
@GetMapping("/bola-issue")
public ResponseEntity<?> getUserBolaIssue(@RequestParam int userId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String loggedInUser = auth.getName();

    User user = getUserById(userId);
    if (user == null) {
        return ResponseEntity.notFound().build();
    }

    Map<String, Object> response = new HashMap<>();
    response.put("requestedUser", user);
    response.put("loggedInAs", loggedInUser);
    response.put("warning", "BOLA Vulnerability - No authorization check!");
    return ResponseEntity.ok(response);
}
```
**Issue:** No check if the authenticated user is authorized to access the requested user data.

---

### Fix 1: Using @SecurityService

**SecurityService:**
```java
@Service("securityService")
public class SecurityService {

    private int getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        //Added fallback for non-custom user details
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            if ("user1".equals(username)) return 1;
            if ("user2".equals(username)) return 2;
        }
        return -1;
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    public boolean isOwner(int userId, Authentication authentication) {
        return isAdmin(authentication) || getCurrentUserId(authentication) == userId;
    }
}
```

**Controller:**
```java
@GetMapping("/bola-fix")
@PreAuthorize("@securityService.isOwner(#userId, authentication)")
public ResponseEntity<?> getUserBolaFix(@RequestParam int userId) {
    User user = getUserById(userId);
    if (user == null) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(user);
}
```
**Advantage:** Reusable authorization logic that can be used across multiple controllers and endpoints.

---

### Fix 2: Database Query Authorization (JPA)

This approach bakes authorization directly into the database query, making it impossible to forget authorization checks.

**Account Entity:**
```java
@Entity
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accountName;
    private String accountNumber;
    private String ownerUsername;
    private double balance;
    // getters and setters
}
```

**Repository with Built-in Authorization:**
```java
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByIdAndOwnerUsername(Long id, String ownerUsername);
}
```

**Controller:**
```java
@GetMapping("/bola-fix/{id}")
public ResponseEntity<?> getAccountBolaFix(@PathVariable Long id, Principal principal) {
    return accountRepository.findByIdAndOwnerUsername(id, principal.getName())
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}
```
**Advantage:** The query only returns accounts owned by the authenticated user. No separate authorization check is needed.

---

### SecurityExceptionHandler - Return 404 instead of 403
```java
@RestControllerAdvice
public class SecurityExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleAccessDeniedException(...) {
        // Return 404 to prevent user enumeration
    }
}
```
**Purpose:** When a user tries to access another user's data without authorization, return 404 instead of 403. This prevents attackers from enumerating valid user IDs.

---

### CustomUserDetails
```java
public class CustomUserDetails implements UserDetails {
    private final int userId;
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public int getUserId() {
        return userId;
    }
    // ... other UserDetails methods
}
```

---
## BOLA Scanning Skill

This repository includes a `bola-security-check` skill for AI tools under `docs/skills` that automatically detects BOLA vulnerabilities in API endpoints. It supports Java/Spring Boot, Node/Express, Node/NestJS, Python/Django, Python/FastAPI, Ruby on Rails, Go/Gin, .NET/ASP.NET Core, and GraphQL.
Copy The skills to your AI tool of choice based on the AI tools skills default directory structure.

### Usage

After adding or modifying an endpoint, ask coding agents:

```
Scan the project for BOLA vulnerabilities
```

The skill will discover endpoints accepting object identifiers, flag missing ownership checks, and guide you through applying the appropriate fix for your framework.

```
🔍 BOLA Scan Results:
   Found 2 endpoint(s) with potential BOLA vulnerability:
   • GET /api/accounts/{id} — accepts id without ownership check
   • GET /api/users/{userId} — accepts userId without ownership check

   Detected framework: Java / Spring Boot

Q: Choose a fix approach (A: @PreAuthorize + SecurityService, B: JPA query-based auth, ...):
```

---

## Security Best Practices

1. **Always validate object-level authorization** - Check if the authenticated user has permission to access the requested resource
2. **Use method-level security annotations** - Leverage `@PreAuthorize` with SpEL expressions for declarative security
3. **Create reusable security services** - Use `@SecurityService` pattern to centralize authorization logic
4. **Extend UserDetails with application-specific fields** - Store `userId` in `CustomUserDetails` for easy access in authorization checks
5. **Implement custom UserDetailsService** - Load user details including application-specific fields from your user store
6. **Fail securely** - Deny access by default when authorization checks fail
7. **Prevent information leakage** - Return generic error responses (404) instead of specific ones (403) to avoid user enumeration attacks
8. **Bake authorization into database queries** - Use parameterized queries that include ownership checks to make authorization a core part of data access
