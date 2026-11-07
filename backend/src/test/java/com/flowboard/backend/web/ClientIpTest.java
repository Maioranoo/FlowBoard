package com.flowboard.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O IP do cliente decide os limites por IP. O cliente controla o começo do X-Forwarded-For
 * (o proxy só acrescenta no fim), então esse começo nunca pode ser usado.
 */
class ClientIpTest {

    @Test
    void prefersTheHeaderSetByCloudflare() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("True-Client-IP", "200.1.1.1");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 200.1.1.1, 10.0.0.1");

        assertThat(ClientIp.of(request)).isEqualTo("200.1.1.1");
    }

    @Test
    void acceptsCfConnectingIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "200.2.2.2");

        assertThat(ClientIp.of(request)).isEqualTo("200.2.2.2");
    }

    @Test
    void ignoresTheForgeableLeftmostForwardedEntry() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // "6.6.6.6" foi escrito pelo próprio cliente; "200.3.3.3" foi acrescentado pelo proxy.
        request.addHeader("X-Forwarded-For", "6.6.6.6, 200.3.3.3");

        assertThat(ClientIp.of(request)).isEqualTo("200.3.3.3");
    }

    @Test
    void fallsBackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThat(ClientIp.of(request)).isEqualTo("127.0.0.1");
    }
}
