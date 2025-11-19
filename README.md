# Quarkus Infinispan DevServices Docker Network Issue Reproducer

This project demonstrates a network connectivity issue with Quarkus Infinispan DevServices when running integration tests with Docker images (`@QuarkusIntegrationTest`).

## The Problem

When running `@QuarkusIntegrationTest` with Docker image testing (`quarkus.container-image.build=true`), the Infinispan DevServices container fails to start with:

```
ERROR [tc.quay.io/infinispan/server:15.0.15.Final]
Could not start container: org.testcontainers.containers.ContainerLaunchException:
Timed out waiting for URL to be accessible (http://infinispan-xxxxx:11222/ should return HTTP 200)
```

### Root Cause

1. **Network Isolation**: When `@QuarkusIntegrationTest` runs, Quarkus creates a Docker network for the integration test container
2. **Infinispan Container**: The Infinispan DevServices container joins this network using `setNetworkMode()`
3. **Testcontainers Ryuk**: Ryuk (Testcontainers' resource reaper) cannot access the container on the isolated network
4. **HTTP Wait Strategy**: The default HTTP-based wait strategy fails because it tries to connect from the host, but the container is only accessible within the Docker network

## Reproduction Steps

### Prerequisites
- Docker
- Java 21+
- Maven

### Reproduce the Issue

1. **Run regular tests** (these will pass):
```bash
./mvnw clean test
```

2. **Run integration tests with Docker** (these will fail with timeout):
```bash
./mvnw clean verify -Dquarkus.container-image.build=true
```

Expected error:
```
Timed out waiting for URL to be accessible
```

### What's Happening

The test execution flow:
1. Maven runs `mvn verify`
2. Quarkus builds a Docker image of the application
3. `@QuarkusIntegrationTest` starts:
   - Creates Docker network (e.g., `network-xyz`)
   - Starts app container on `network-xyz`
   - Starts Infinispan DevServices container
4. Infinispan container joins `network-xyz` using `setNetworkMode()`
5. **FAILURE**: Testcontainers' HTTP wait strategy cannot verify container health from host
6. Timeout after waiting for HTTP 200 response

## The Solution

The fix requires modifying `QuarkusInfinispanContainer` in Quarkus framework to use a **log-based wait strategy** instead of HTTP, following the same pattern as Keycloak DevServices.

### Fix Location

File: `quarkus/extensions/infinispan-client/deployment/src/main/java/io/quarkus/infinispan/client/deployment/devservices/InfinispanDevServiceProcessor.java`

### Required Change

In the `QuarkusInfinispanContainer` constructor (around line 338):

```java
withCommand(command);

// ADD THESE TWO LINES (same pattern as Keycloak DevServices):
super.setWaitStrategy(Wait.forLogMessage(".*Infinispan Server.*started.*", 1));
this.hostName = ConfigureUtil.configureNetwork(this, defaultNetworkId, useSharedNetwork, "infinispan");
```

Don't forget to add the import:
```java
import org.testcontainers.containers.wait.strategy.Wait;
```

### Why This Works

1. **Log-based wait strategies** check Docker daemon logs, not network endpoints
2. Works regardless of network isolation
3. Compatible with Testcontainers' Ryuk
4. Same proven pattern used by Keycloak DevServices
5. No conditional logic needed - works in all scenarios:
   - Regular dev mode
   - Unit tests (`@QuarkusTest`)
   - Integration tests (`@QuarkusIntegrationTest`)
   - Shared network mode

## Project Structure

```
src/
├── main/
│   ├── java/org/acme/
│   │   ├── CacheService.java         # Infinispan cache service
│   │   └── GreetingResource.java     # REST endpoints with cache
│   └── resources/
│       └── application.properties     # Infinispan + Docker config
└── test/
    └── java/org/acme/
        ├── GreetingResourceTest.java  # Unit tests (pass)
        └── GreetingResourceIT.java    # Integration tests (fail without fix)
```

## Testing the Fix

After applying the fix to Quarkus:

1. Build Quarkus with the fix
2. Update this project to use the fixed Quarkus version
3. Run integration tests:
```bash
./mvnw clean verify -Dquarkus.container-image.build=true
```

Tests should now pass! ✅

## Comparison with Keycloak DevServices

### Keycloak (Working)
```java
// KeycloakDevServicesProcessor.java:536-537
super.setWaitStrategy(Wait.forLogMessage(".*Keycloak.*started.*", 1));
this.hostName = ConfigureUtil.configureNetwork(this, defaultNetworkId, useSharedNetwork, "keycloak");
```

### Infinispan (Before Fix)
```java
// InfinispanDevServiceProcessor.java:339
// Missing wait strategy configuration!
this.hostName = ConfigureUtil.configureNetwork(this, defaultNetworkId, useSharedNetwork, "infinispan");
```

### Infinispan (After Fix)
```java
// InfinispanDevServiceProcessor.java:338-340
withCommand(command);
super.setWaitStrategy(Wait.forLogMessage(".*Infinispan Server.*started.*", 1));
this.hostName = ConfigureUtil.configureNetwork(this, defaultNetworkId, useSharedNetwork, "infinispan");
```

## API Endpoints

The reproducer includes these endpoints for testing:

- `GET /hello` - Basic greeting
- `GET /hello/cache/health` - Check Infinispan connection
- `GET /hello/cache/{key}` - Get value from cache
- `GET /hello/cache/{key}/{value}` - Put value in cache

## Related Issues

- Testcontainers Ryuk cannot access containers on isolated networks
- HTTP wait strategies fail when container is only accessible within Docker network
- `setNetworkMode()` isolates container from host network

## References

- [Quarkus Issue Tracker](https://github.com/quarkusio/quarkus/issues)
- [Testcontainers Documentation](https://java.testcontainers.org/)
- Keycloak DevServices implementation (reference)
- Infinispan DevServices implementation (to be fixed)
