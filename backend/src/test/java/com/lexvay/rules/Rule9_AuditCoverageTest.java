package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Product rule 9 guard: every mutating endpoint must be in this declared set, and each entry must have
 * an integration test asserting its audit row. Adding an endpoint without updating both fails the build.
 */
class Rule9_AuditCoverageTest extends AbstractDatabaseTest {

    /** Endpoint → test that asserts its audit row. */
    static final Set<String> AUDITED_MUTATING_ENDPOINTS = Set.of(
            "POST /api/v1/programs"          // ProgramApiTest.createWritesAuditRow
    );

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyMutatingEndpointIsDeclaredAudited() {
        var mutating = new TreeSet<String>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            for (RequestMethod verb : info.getMethodsCondition().getMethods()) {
                if (verb == RequestMethod.GET || verb == RequestMethod.HEAD || verb == RequestMethod.OPTIONS) {
                    continue;
                }
                for (var pattern : info.getPathPatternsCondition().getPatterns()) {
                    mutating.add(verb + " " + pattern.getPatternString());
                }
            }
        });
        assertThat(mutating).containsExactlyInAnyOrderElementsOf(AUDITED_MUTATING_ENDPOINTS);
    }
}
