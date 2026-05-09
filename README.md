# BOLA Security Issue Demo

A Spring Boot project demonstrating the BOLA (Broken Object Level Authorization) vulnerability and its fix.

## Overview

BOLA occurs when an application exposes endpoints that handle object identifiers without proper authorization checks, allowing attackers to access unauthorized data by modifying the object ID in requests.

## Endpoints

1. `/api/users/bola-issue?userId={id}` - Vulnerable endpoint .
2. `/api/users/bola-fix?userId={id}` |- Fixed endpoint - validates ownership.

## Prerequisites

- Java 17+
- Maven 3.6+

## How to Run

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## How to Test BOLA Issues

### Test Users

| Username | Password | Role | User ID |
|----------|----------|------|---------|
| user1 | password | USER | 1 |
| user2 | password | USER | 2 |

### Testing the BOLA Vulnerability

**1. Test the VULNERABLE endpoint (`/bola-issue`)**

Access own data (as user1):
```bash
curl -u user1:password "http://localhost:8080/api/users/bola-issue?userId=1"
```
Result: Shows user1 is logged in and can access Alice's data.

Access another user's data while authenticated as user1 (BOLA vulnerability):
```bash
curl -u user1:password "http://localhost:8080/api/users/bola-issue?userId=2"
```
Result: **VULNERABLE** - Shows "loggedInAs": "user1" but returns Bob's data. This demonstrates the BOLA vulnerability - no authorization check is performed.

**2. Test the FIXED endpoint (`/bola-fix`)**

Access own data (allowed):
```bash
curl -u user1:password "http://localhost:8080/api/users/bola-fix?userId=1"
curl -u user2:password "http://localhost:8080/api/users/bola-fix?userId=2"
```
Expected: Returns the correct user data - **Success**

Access another user's data (blocked):
```bash
curl -u user1:password "http://localhost:8080/api/users/bola-fix?userId=2"
```
Expected: **404 Not Found** - Returns the same response as invalid user ID to prevent information leakage

### Testing with Invalid User ID

```bash
curl -u user1:password "http://localhost:8080/api/users/bola-fix?userId=999"
```
Expected: 404 Not Found

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
**Issue:** Authentication is performed, but no check if the authenticated user is authorized to access the requested user data. The response shows who is logged in, making the vulnerability obvious.

### Fixed Code (bola-fix) - Using @PreAuthorize
```java
@GetMapping("/bola-fix")
@PreAuthorize("#userId == authentication.principal.userId or hasRole('ADMIN')")
public ResponseEntity<?> getUserBolaFix(@RequestParam int userId) {
    User user = getUserById(userId);
    if (user == null) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(user);
}
```
**Fix:** Uses `@PreAuthorize` with SpEL to verify that the authenticated user is accessing their own data. The `authentication.principal.userId` is retrieved from `CustomUserDetails`.

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

## Security Best Practices

1. **Always validate object-level authorization** - Check if the authenticated user has permission to access the requested resource
2. **Use method-level security annotations** - Leverage `@PreAuthorize` with SpEL expressions for declarative security
3. **Extend UserDetails with application-specific fields** - Store `userId` in `CustomUserDetails` for easy access in authorization checks
4. **Implement custom UserDetailsService** - Load user details including application-specific fields from your user store
5. **Fail securely** - Deny access by default when authorization checks fail
6. **Prevent information leakage** - Return generic error responses (404) instead of specific ones (403) to avoid user enumeration attacks
