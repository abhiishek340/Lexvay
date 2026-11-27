package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The address policy (D-182), with name resolution replaced by {@link FakeHostResolver}: no test needs DNS. */
class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard(new FakeHostResolver());

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {
            "http://127.0.0.1/rules", "http://127.1.2.3:8080/rules", "http://10.0.0.1/rules", "http://172.16.0.1/rules",
            "http://172.31.255.255/rules", "http://192.168.1.1/rules", "http://169.254.1.1/rules",
            "http://169.254.169.254/latest/meta-data", "http://0.0.0.0/rules", "http://100.64.0.1/rules",
            "http://224.0.0.1/rules", "http://[::1]/rules", "http://[::]/rules", "http://[fe80::1]/rules",
            "http://[fd00::1]/rules", "http://[fc00::1]/rules", "http://[ff02::1]/rules",
            "http://[::ffff:10.0.0.1]/rules", "http://[::ffff:127.0.0.1]/rules", "http://[::10.0.0.1]/rules"})
    void internalAddressesAreRefused(String url) {
        assertThatThrownBy(() -> guard.requireAllowed(URI.create(url)))
                .isInstanceOf(FetchRejected.class)
                .hasMessageContaining("internal address");
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"ftp://rules.example.test/rules.pdf", "file:///etc/passwd", "gopher://rules.example.test/",
            "jar:http://rules.example.test/a.jar!/", "javascript:alert(1)", "http:///rules"})
    void onlyHttpAndHttpsWithAHostAreAllowed(String url) {
        assertThatThrownBy(() -> guard.requireAllowed(URI.create(url))).isInstanceOf(FetchRejected.class);
    }

    @Test
    void aNameThatResolvesToAPrivateOrMetadataAddressIsRefused() {
        assertThatThrownBy(() -> guard.requireAllowed(URI.create("https://" + FakeHostResolver.PRIVATE_HOST + "/rules")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining(FakeHostResolver.PRIVATE_HOST);
        assertThatThrownBy(() -> guard.requireAllowed(URI.create("https://" + FakeHostResolver.METADATA_HOST + "/rules")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("internal address");
    }

    @Test
    void oneInternalAddressAmongPublicOnesIsEnoughToRefuse() {
        SsrfGuard mixed = new SsrfGuard(host -> new InetAddress[] {
                InetAddress.getByName("203.0.113.10"), InetAddress.getByName("10.1.2.3")});
        assertThatThrownBy(() -> mixed.requireAllowed(URI.create("https://rules.example.test/")))
                .isInstanceOf(FetchRejected.class);
    }

    @Test
    void aNameThatDoesNotResolveIsRefused() {
        assertThatThrownBy(() -> guard.requireAllowed(URI.create("https://nowhere.example.test/rules")))
                .isInstanceOf(FetchRejected.class).hasMessageContaining("cannot be resolved")
                .hasCauseInstanceOf(UnknownHostException.class);
    }

    @ParameterizedTest(name = "{0} is allowed")
    @ValueSource(strings = {"https://rules.example.test/rules.pdf", "HTTP://rules.example.test:8443/a?b=c#d",
            "http://203.0.113.10/rules", "http://8.8.8.8/rules", "http://[2001:4860:4860::8888]/rules"})
    void publicAddressesAreAllowed(String url) {
        assertThatCode(() -> guard.requireAllowed(URI.create(url))).doesNotThrowAnyException();
    }

    @Test
    void theEdgesOfTheBlockedRangesAreExact() throws UnknownHostException {
        assertThat(SsrfGuard.isInternal(InetAddress.getByName("172.15.255.255"))).isFalse();
        assertThat(SsrfGuard.isInternal(InetAddress.getByName("172.32.0.0"))).isFalse();
        assertThat(SsrfGuard.isInternal(InetAddress.getByName("100.63.255.255"))).isFalse();
        assertThat(SsrfGuard.isInternal(InetAddress.getByName("100.127.255.255"))).isTrue();
        assertThat(SsrfGuard.isInternal(InetAddress.getByName("100.128.0.0"))).isFalse();
        assertThat(SsrfGuard.isInternal(InetAddress.getByName("fbff::1"))).isFalse();
        assertThat(SsrfGuard.isInternal(InetAddress.getByName("fdff::1"))).isTrue();
        assertThat(SsrfGuard.isInternal(InetAddress.getByName("fe00::1"))).isFalse();
    }
}
