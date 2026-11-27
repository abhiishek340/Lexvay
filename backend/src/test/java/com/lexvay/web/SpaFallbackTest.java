package com.lexvay.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestCsrf;
import com.lexvay.common.TestTenants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.client.RestClient;

/** The image serves the Angular bundle from the classpath; the test classpath holds a stand-in shell and asset. */
class SpaFallbackTest extends AbstractDatabaseTest {

    @LocalServerPort int port;
    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;

    private static String body(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/index.html", "/programs", "/programs/0d0b4e5c-4a33-4d58-9d4b-111111111111/review",
            "/assessments/x/mappings/y", "/v1.2/dashboard", "/login"})
    void routesWithoutAFileReturnTheShellWithoutSignIn(String path) throws Exception {
        var result = mvc.get().uri(path).exchange();
        assertThat(result).hasStatus(200);
        assertThat(result.getResponse().getContentType()).startsWith("text/html");
        assertThat(body(result)).contains("<app-root>");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-cache");
    }

    @Test
    void theRootIsTheShellToo() {
        var response = RestClient.create("http://localhost:" + port).get().uri("/").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("<app-root>");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
    }

    @Test
    void aFileFromTheBundleIsServedAsItIsAndCachedForAYear() throws Exception {
        var result = mvc.get().uri("/main-ABCD1234.js").exchange();
        assertThat(result).hasStatus(200);
        assertThat(body(result)).contains("hashed bundle");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("max-age=31536000, public, immutable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/missing.js", "/assets/logo.png", "/deep/path/file.css"})
    void aMissingFileIsA404ProblemDetailNotTheShell(String path) throws Exception {
        var result = mvc.get().uri(path).exchange();
        assertThat(result).hasStatus(404);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    }

    @Test
    void anUnknownApiPathStaysA404ProblemDetailForASignedInUser() throws Exception {
        var t = tenants.create("spa");
        for (String path : new String[] {"/api/unknown", "/api/v1/unknown", "/api/v1/programs/x/y/z", "/api"}) {
            var result = mvc.get().uri(path).with(user(t.reviewer())).exchange();
            assertThat(result).as(path).hasStatus(404);
            assertThat(result.getResponse().getContentType()).as(path).startsWith("application/problem+json");
            assertThat(body(result)).as(path).doesNotContain("<app-root>");
        }
    }

    @Test
    void theApiAndTheActuatorStillRequireSignIn() {
        assertThat(mvc.get().uri("/api/unknown").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/programs").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/actuator/prometheus").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/actuator/anything").exchange()).hasStatus(401);
    }

    @Test
    void onlyGetReachesTheShell() {
        assertThat(mvc.post().uri("/programs/abc").with(TestCsrf.csrf()).exchange()).hasStatus(401);
        assertThat(mvc.delete().uri("/programs/abc").with(TestCsrf.csrf()).exchange()).hasStatus(401);
    }

    @Test
    void routeAndServicePathRules() {
        assertThat(SpaRoutes.isRoute("programs/1")).isTrue();
        assertThat(SpaRoutes.isRoute("v1.2/dashboard")).isTrue();
        assertThat(SpaRoutes.isRoute("main-ABCD1234.js")).isFalse();
        assertThat(SpaRoutes.isService("/api")).isTrue();
        assertThat(SpaRoutes.isService("/api/v1/x")).isTrue();
        assertThat(SpaRoutes.isService("/actuator/health")).isTrue();
        assertThat(SpaRoutes.isService("/apiary")).isFalse();
        assertThat(SpaRoutes.isService("/programs/api")).isFalse();
    }
}
