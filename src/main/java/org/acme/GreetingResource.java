package org.acme;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
public class GreetingResource {

    @Inject
    CacheService cacheService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus REST";
    }

    @GET
    @Path("/cache/health")
    @Produces(MediaType.TEXT_PLAIN)
    public String cacheHealth() {
        boolean connected = cacheService.isConnected();
        return connected ? "Infinispan is connected!" : "Infinispan connection failed!";
    }

    @GET
    @Path("/cache/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getFromCache(@PathParam("key") String key) {
        return cacheService.get(key).orElse("Not found");
    }

    @GET
    @Path("/cache/{key}/{value}")
    @Produces(MediaType.TEXT_PLAIN)
    public String putInCache(@PathParam("key") String key, @PathParam("value") String value) {
        cacheService.put(key, value);
        return "Cached: " + key + " = " + value;
    }
}
