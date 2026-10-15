package com.virtualkitchen.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI document for the web app's generated TypeScript client.
 *
 * <p>Response DTOs are records whose fields are always present in the JSON (null or not), so every
 * property of a schema used only in responses is marked {@code required}. Fields that can be null
 * carry {@code @Schema(nullable = true)}, which becomes {@code T | null} in TypeScript. Schemas
 * reachable from a request body keep springdoc's default (required = validated with @NotNull etc.).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI virtualKitchenOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Virtual Live Kitchen API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public OpenApiCustomizer responseFieldsRequired() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            if (schemas == null) {
                return;
            }
            Set<String> requestSchemas = requestBodySchemas(openApi, schemas);
            schemas.forEach((name, schema) -> {
                // A field's @Schema(nullable = true) leaks onto the referenced component; nullability belongs
                // to the field (the allOf wrapper below), so a type used elsewhere as non-null stays non-null.
                schema.setNullable(null);
                if (!requestSchemas.contains(name) && schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            });
        };
    }

    /**
     * OpenAPI 3.0 ignores siblings of {@code $ref}, so {@code @Schema(nullable = true)} on a nested
     * object field would be lost. Wrap such fields as {@code allOf: [$ref]} + {@code nullable}.
     */
    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public PropertyCustomizer nullableObjectFields() {
        return (property, type) -> {
            boolean nullable = type.getCtxAnnotations() != null && java.util.Arrays.stream(type.getCtxAnnotations())
                    .anyMatch(a -> a instanceof io.swagger.v3.oas.annotations.media.Schema s && s.nullable());
            if (nullable && property.get$ref() != null) {
                Schema wrapped = new Schema();
                wrapped.setAllOf(List.of(new Schema().$ref(property.get$ref())));
                wrapped.setNullable(true);
                return wrapped;
            }
            return property;
        };
    }

    /** Names of all schemas reachable from any request body (transitively through $refs). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Set<String> requestBodySchemas(OpenAPI openApi, Map<String, Schema> schemas) {
        Deque<Schema> todo = new ArrayDeque<>(); // swagger-models uses raw Schema types throughout
        openApi.getPaths().values().forEach(path -> path.readOperations().stream()
                .map(Operation::getRequestBody)
                .filter(body -> body != null && body.getContent() != null)
                .map(body -> body.getContent())
                .flatMap((Content content) -> content.values().stream())
                .forEach(media -> todo.add(media.getSchema())));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            Schema schema = todo.pop();
            if (schema == null) {
                continue;
            }
            if (schema.get$ref() != null) {
                String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
                if (seen.add(name) && schemas.containsKey(name)) {
                    todo.add(schemas.get(name));
                }
                continue;
            }
            if (schema.getItems() != null) {
                todo.add(schema.getItems());
            }
            if (schema.getProperties() != null) {
                todo.addAll(schema.getProperties().values());
            }
            if (schema.getAdditionalProperties() instanceof Schema additional) {
                todo.add(additional);
            }
            for (List<Schema> composed : java.util.Arrays.asList(schema.getAllOf(), schema.getOneOf(), schema.getAnyOf())) {
                if (composed != null) {
                    composed.forEach(todo::add);
                }
            }
        }
        return seen;
    }
}
