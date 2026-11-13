package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * OWASP API9 (Improper Inventory Management): {@code docs/API_INVENTORY.md}
 * lists every endpoint the backend serves, with who may call it, and this test
 * fails when the running application and the file disagree. An endpoint added,
 * removed or opened to another role therefore shows up in the commit's diff,
 * and the inventory can never fall behind the code.
 * <p>
 * After a deliberate change, regenerate the file and commit it:
 * {@code mvn test -Dtest=ApiInventoryTest -Dapi.inventory.update=true}.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiInventoryTest {

    static final Path INVENTORY = Path.of("docs", "API_INVENTORY.md");

    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    @Test
    @DisplayName("docs/API_INVENTORY.md lists exactly the endpoints the application serves")
    void inventoryMatchesTheApplication() throws IOException {
        String expected = render(mappings.getHandlerMethods());

        if (Boolean.getBoolean("api.inventory.update")) {
            Files.writeString(INVENTORY, expected, StandardCharsets.UTF_8);
        }

        String committed = Files.exists(INVENTORY)
                ? Files.readString(INVENTORY, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
        assertThat(committed)
                .as("docs/API_INVENTORY.md is out of date: run "
                        + "mvn test -Dtest=ApiInventoryTest -Dapi.inventory.update=true and commit it")
                .isEqualTo(expected);
    }

    static String render(Map<RequestMappingInfo, HandlerMethod> handlers) {
        Set<String> rows = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlers.entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.donatodev")) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            String verbs = methods.isEmpty() ? "ANY" : String.join(", ", new TreeSet<>(methods.stream().map(Enum::name).toList()));
            for (String path : info.getPatternValues()) {
                rows.add("| `" + path + "` | " + verbs + " | " + access(handler) + " | `"
                        + handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName() + "` |");
            }
        }
        StringBuilder out = new StringBuilder()
                .append("# API inventory\n\n")
                .append("Every endpoint the backend serves under `/api/v1`, with who may call it (OWASP API9).\n")
                .append("Generated from the running application by `ApiInventoryTest`, which fails when this file\n")
                .append("and the code disagree: don't edit it by hand, regenerate it with\n")
                .append("`mvn test -Dtest=ApiInventoryTest -Dapi.inventory.update=true`.\n\n")
                .append("\"No role\" endpoints are public by nature or act on the caller's own account; each is\n")
                .append("listed with its reason in `FunctionLevelAuthorizationTest` (OWASP API5). The full\n")
                .append("OpenAPI schema is served at `/api/v1/api-docs` outside production.\n\n")
                .append(rows.size()).append(" endpoints.\n\n")
                .append("| Path | Method | Access | Handler |\n")
                .append("|---|---|---|---|\n");
        rows.forEach(row -> out.append(row).append('\n'));
        return out.toString();
    }

    private static String access(HandlerMethod handler) {
        PreAuthorize rule = handler.getMethodAnnotation(PreAuthorize.class);
        if (rule == null) {
            rule = handler.getBeanType().getAnnotation(PreAuthorize.class);
        }
        return rule == null ? "no role" : "`" + rule.value() + "`";
    }
}
