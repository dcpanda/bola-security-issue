---
name: bola-security-check
description: >-
  Detect and fix Broken Object Level Authorization (BOLA) vulnerabilities
  in REST APIs and GraphQL endpoints across Java/Spring Boot, Node/Express,
  Node/NestJS, Python/Django, Python/FastAPI, Ruby on Rails, Go/Gin,
  .NET/ASP.NET Core, and GraphQL implementations.
license: MIT
metadata:
  category: security
  languages: java,javascript,python,ruby,go,csharp,graphql
  audit: endpoints
---

# BOLA Security Check

## What I Do

Detect **Broken Object Level Authorization** (BOLA / IDOR) vulnerabilities in newly created or modified API endpoints by scanning for object identifier references without ownership verification, then guide the developer through fixing them.

---

## Phase 1 — Endpoint Discovery

Scan the codebase to discover all API endpoints that accept object identifiers. Look for:

### REST API Patterns

| Framework | What to Scan | What to Look For |
|-----------|-------------|------------------|
| **Spring Boot** | `@RestController` classes | `@PathVariable`, `@RequestParam` with names like `id`, `userId`, `accountId`, `objectId` |
| **Express.js** | `router.get/post/put/delete` calls | Route patterns with `:id`, `:userId`, `:accountId` |
| **NestJS** | `@Controller` classes with `@Get`, `@Post`, etc. | `@Param()`, `@Query()` decorators for object IDs |
| **Django REST** | `views.py`, `api_views.py` | URL patterns with `<int:id>`, `<int:pk>`, `<uuid:id>` |
| **FastAPI** | `@app.get/post/put/delete`, `APIRouter` | Path parameters, `Query()` parameters named `id`, `user_id` |
| **Ruby on Rails** | `config/routes.rb`, controllers | RESTful routes with `:id`, `:user_id` |
| **Go / Gin** | `router.GET/POST/PUT/DELETE` | `:id`, `:userId` in path patterns, `c.Param("id")` |
| **.NET / ASP.NET Core** | `[ApiController]` classes | `[FromRoute]`, `[FromQuery]` with `id` parameters |
| **GraphQL** | schema files, resolver/query files | Query/mutation arguments named `id`, `userId` on object fetch operations |

### What to Flag

Any endpoint that:
1. Accepts an object identifier (path param, query param, request body field, or GraphQL argument)
2. Fetches a resource using that identifier (`findById`, `find`, `get`, `first`, `GetById`, etc.)
3. Does **NOT** verify the authenticated user owns the resource before returning it

---

## Phase 2 — Vulnerability Detection

For each discovered endpoint, determine if an ownership check exists.

### Quick Check Patterns

**Vulnerable** (flag these):
- `repository.findById(id)` without checking user
- `repository.findByPk(id)` without user filter
- `Model.query().findById(id)` with no `.where(userId)`
- `db.collection.find(id)` with no user scope
- GraphQL resolver that fetches by ID without comparing `context.user` to the resource owner
- `accountService.getById(id)` where the service never checks ownership

**Safe** (do not flag):
- `repository.findByIdAndOwnerUsername(id, currentUser)`
- `repository.findByPk(id)` where `findByPk` already includes tenant/user scope
- Query with `.where(userId: currentUser.id)` appended
- Service layer that injects current user and filters by it
- `@PreAuthorize` or equivalent guard checking ownership

### Secondary Heuristics

- Look for authorization annotations/decorators above the endpoint method
- Check if a middleware/guard/filter is applied to the route that performs ownership checks
- Examine GraphQL directives that handle authorization
- Check if `Authentication` / `Principal` / `context.user` is accepted in the method signature but **not used** in the data access call

---

## Phase 3 — Language Detection

Examine project build files to determine the language and framework:

| File to Check | Matches | Framework |
|---------------|---------|-----------|
| `pom.xml` or `build.gradle` | `spring-boot-starter-web` | Java / Spring Boot |
| `package.json` | `express` | Node / Express |
| `package.json` | `@nestjs/core` | Node / NestJS |
| `requirements.txt` or `Pipfile` | `django`, `djangorestframework` | Python / Django DRF |
| `requirements.txt` or `pyproject.toml` | `fastapi` | Python / FastAPI |
| `Gemfile` | `rails`, `pundit`, `cancancan` | Ruby on Rails |
| `go.mod` | `gin-gonic/gin` | Go / Gin |
| `*.csproj` | `Microsoft.AspNetCore` | .NET / ASP.NET Core |
| `package.json` | `graphql`, `apollo-server`, `@nestjs/graphql` | GraphQL (Node) |
| `pom.xml` or `build.gradle` | `graphql-spring-boot`, `graphql-java` | GraphQL (Java) |
| `Gemfile` | `graphql-ruby` | GraphQL (Ruby) |
| `*.csproj` | `HotChocolate.AspNetCore` | GraphQL (.NET) |
| None of the above | — | Unknown / Generic |

---

## Phase 4 — User Prompt

Report the findings to the user and present fix options:

```
🔍 BOLA Scan Results:
   Found <N> endpoint(s) with potential BOLA vulnerability:
   • <method> <path> — accepts <id-param> without ownership check
   • <method> <path> — accepts <id-param> without ownership check

   Detected framework: <Framework Name>

Q: Choose a fix approach (or type "default" for high-level guidance):
```

### Fix Options Per Framework

#### Java / Spring Boot

| Option | Label | Description |
|--------|-------|-------------|
| **A** | `@PreAuthorize` + SecurityService | Create a reusable `SecurityService` bean and annotate endpoints with `@PreAuthorize("@securityService.isOwner(#id, authentication)")`. Centralized, annotation-driven. |
| **B** | JPA query-based auth | Add `findByIdAndOwnerUsername(id, username)` to the repository. Authorization is baked into the data access layer. Stealthier (no separate auth check). |
| **C** | Custom Filter/Interceptor | Build a `OncePerRequestFilter` or `HandlerInterceptor` that inspects the request and checks ownership before the controller executes. |
| **D** | 403 → 404 hardening | Wrap `AccessDeniedException` into `404 NOT FOUND` via `@RestControllerAdvice` to prevent ID enumeration attacks. Apply this on top of A, B, or C. |

#### Node / Express

| Option | Label | Description |
|--------|-------|-------------|
| **A** | Route-level middleware | Create a reusable `checkOwnership(modelName)` middleware that extracts the ID, loads the resource, compares `resource.userId` to `req.user.id`, and returns 404/403 on mismatch. |
| **B** | Service-layer injection | Modify service functions to accept `currentUser` and scope queries accordingly (e.g., `findByIdAndUser(id, userId)`). |
| **C** | Custom Express middleware | Build a generic `authorize` middleware that inspects route params and user context dynamically. |

#### Node / NestJS

| Option | Label | Description |
|--------|-------|-------------|
| **A** | Custom Guard | Create a `BolaGuard` implementing `CanActivate` that extracts the resource ID from the request, loads the entity, and compares ownership against the authenticated user. |
| **B** | Service-layer scoping | Inject `@Req() user` into services and scope all `findOne` / `findById` calls with the current user's identity. |
| **C** | Custom Pipe/Interceptor | Build a pipe that validates resource ownership before the handler executes. |

#### Python / Django REST Framework

| Option | Label | Description |
|--------|-------|-------------|
| **A** | Custom Permission class | Create a `IsOwner` permission class that overrides `has_object_permission` to compare `obj.user` (or owner field) with `request.user`. |
| **B** | Queryset override | Override `get_queryset()` in the view to filter by the current user (e.g., `self.request.user`), making authorization part of data access. Stealthier. |
| **C** | Mixin-based ownership | Create a reusable `OwnerQuerySetMixin` that automatically scopes all queries to the authenticated user. |

#### Python / FastAPI

| Option | Label | Description |
|--------|-------|-------------|
| **A** | Dependency-based auth | Create a reusable `get_resource_or_404(resource_id, user)` dependency that loads the resource and checks `resource.owner_id == user.id`. |
| **B** | Service-layer scoping | Inject the current user into service methods and scope all queries via the user ID. |

#### Ruby on Rails

| Option | Label | Description |
|--------|-------|-------------|
| **A** | Pundit policy | Generate a Pundit policy per resource with an `update?` / `show?` method that checks `record.user == user`. Apply via `authorize @resource` in controllers. |
| **B** | CanCanCan abilities | Define abilities in `Ability.rb` with `can :manage, Resource, user_id: user.id`. Apply via `load_and_authorize_resource`. |
| **C** | Manual scoping | Scope queries directly in the controller: `current_user.resources.find(params[:id])` instead of `Resource.find(params[:id])`. |

#### Go / Gin

| Option | Label | Description |
|--------|-------|-------------|
| **A** | Middleware with context | Create a middleware that extracts the resource ID from `c.Param("id")`, loads the resource, checks `resource.UserID == c.GetInt("userID")`, and aborts with 404 on mismatch. |
| **B** | Service-layer check | Modify service functions to accept a `userID` parameter and filter queries accordingly. Protected at the data layer. |

#### .NET / ASP.NET Core

| Option | Label | Description |
|--------|-------|-------------|
| **A** | Policy-based authorization | Register a `ResourceOwnerRequirement` + `AuthorizationHandler` that validates ownership. Apply via `[Authorize(Policy = "IsResourceOwner")]` on controllers/actions. |
| **B** | Custom AuthorizationHandler | Build a handler that extracts the resource ID from route data, loads the entity, and checks `entity.UserId == currentUserId`. |
| **C** | Service-layer scoping | Inject `IHttpContextAccessor` into services and scope data access queries to the current user's identity. |

#### GraphQL (All Languages)

| Option | Label | Description |
|--------|-------|-------------|
| **A** | Custom directive | Create a `@requiresOwnership` directive/decorator that wraps field resolvers. The directive extracts the parent object's owner field and compares it to `context.user`. |
| **B** | Resolver-level DataLoader auth | Use DataLoaders that incorporate the current user into their batch load functions, so that unowned resources return `null` instead of the data. Prevents over-fetching at the loader level. |
| **C** | Resolver-level check | Add an explicit ownership check inside each resolver after fetching the resource. Most straightforward but easy to forget on new resolvers. |

#### Unknown / Generic Framework

| Option | Label | Description |
|--------|-------|-------------|
| **A** | Middleware pattern | Add a request-scoped middleware that captures the route ID parameter and performs an ownership check before the endpoint handler runs. |
| **B** | Service-layer pattern | Pass the authenticated user's identity into every data-access method and scope queries by user/tenant. |
| **C** | Check existing pattern | Inspect other endpoints in the project that DO have authorization and replicate their pattern. |

### Default Behavior

If the user replies with **"default"** or does not specify an option:
- Follow existing authorization patterns already present in the codebase
- If no patterns exist, use the service-layer scoping approach (Option B for most frameworks) as the safest default
- Preserve existing code style and conventions

---

## Phase 5 — Implementation

### Before Writing Code

1. Read 2-3 existing controller/service files to understand:
   - Code style and conventions (naming, import style, error handling)
   - How authentication is accessed (Principal, Authentication, `req.user`, `request.user`, `context.user`)
   - How the data layer works (JPA, Mongoose, ActiveRecord, SQLAlchemy, etc.)
   - What user model fields exist (owner, user_id, userId, ownerUsername, etc.)

2. Identify the **resource model** and its **owner field** (e.g., `Account.userId`, `Post.author_id`, `Resource.owner`).

### Implementation Steps (General)

1. **Create the authorization building block** (SecurityService, middleware, guard, policy, permission class, handler, or directive).
2. **Apply it to the vulnerable endpoint(s)** (annotation, decorator, middleware registration, or inline scoping).
3. **Add error hardening** if applicable (convert 403 to 404 to prevent ID enumeration).
4. **Follow existing project patterns** exactly — do not introduce new conventions.

### Java / Spring Boot — Reference Implementation

The reference project at `src/main/java/com/security/demo/` demonstrates two approaches:

**Approach A — SecurityService + `@PreAuthorize`:**

1. Create `SecurityService`:
   ```java
   @Service("securityService")
   public class SecurityService {
       public boolean isOwner(int userId, Authentication authentication) {
           if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
               return userDetails.getUserId() == userId || isAdmin(authentication);
           }
           return false;
       }
       private boolean isAdmin(Authentication authentication) {
           return authentication.getAuthorities().stream()
               .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
       }
   }
   ```
2. Apply to controller:
   ```java
   @GetMapping("/bola-fix")
   @PreAuthorize("@securityService.isOwner(#userId, authentication)")
   public ResponseEntity<?> getFixed(@RequestParam int userId) { ... }
   ```

**Approach B — JPA query-based auth:**

1. Add to repository:
   ```java
   Optional<Account> findByIdAndOwnerUsername(Long id, String ownerUsername);
   ```
2. Use in controller:
   ```java
   @GetMapping("/bola-fix/{id}")
   public ResponseEntity<?> getFixed(@PathVariable Long id, Principal principal) {
       return accountRepository.findByIdAndOwnerUsername(id, principal.getName())
           .map(ResponseEntity::ok)
           .orElse(ResponseEntity.notFound().build());
   }
   ```

**Hardening — 403 to 404:**
```java
@RestControllerAdvice
public class SecurityExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleAccessDenied(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }
}
```

### Verification Checklist

After implementing the fix, the agent MUST verify:

- [ ] The code compiles (Java: `mvn compile`, Node: `npm run build`, Python: `python -m py_compile`, etc.)
- [ ] Lint passes (check for relevant lint/typecheck command in project)
- [ ] No existing tests are broken (run test suite if one exists)
- [ ] The fix follows existing project conventions (same auth pattern, same error handling style)
- [ ] The fix is applied to ALL flagged endpoints, not just one
