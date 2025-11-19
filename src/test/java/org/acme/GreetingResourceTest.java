package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class GreetingResourceTest {

    @Test
    void testHelloEndpoint() {
        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .body(is("Hello from Quarkus REST"));
    }

    @Test
    void testCacheHealth() {
        given()
          .when().get("/hello/cache/health")
          .then()
             .statusCode(200)
             .body(containsString("Infinispan is connected!"));
    }

    @Test
    void testCachePutAndGet() {
        // Put value in cache
        given()
          .when().get("/hello/cache/testKey/testValue")
          .then()
             .statusCode(200)
             .body(containsString("Cached: testKey = testValue"));

        // Get value from cache
        given()
          .when().get("/hello/cache/testKey")
          .then()
             .statusCode(200)
             .body(is("testValue"));
    }
}
