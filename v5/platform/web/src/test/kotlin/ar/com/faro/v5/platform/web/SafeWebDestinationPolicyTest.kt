package ar.com.faro.v5.platform.web

import java.net.InetAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import okhttp3.Dns

class SafeWebDestinationPolicyTest {
    @Test
    fun `public IPv4 is allowed while internal and special ranges are denied`() {
        assertTrue(allows("93.184.216.34"))

        listOf(
            "0.0.0.1",
            "10.1.2.3",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.169.254",
            "172.16.0.1",
            "192.0.0.8",
            "192.0.2.10",
            "192.88.99.2",
            "192.168.1.1",
            "198.18.0.1",
            "198.51.100.10",
            "203.0.113.10",
            "224.0.0.1",
            "255.255.255.255",
        ).forEach { address ->
            assertTrue(!allows(address), address)
        }
    }

    @Test
    fun `public IPv6 is allowed while local transition and documentation ranges are denied`() {
        assertTrue(allows("2606:4700:4700::1111"))
        assertTrue(allows("2620:fe::fe"))

        listOf(
            "::",
            "::1",
            "::ffff:127.0.0.1",
            "64:ff9b::7f00:1",
            "100::1",
            "2001::1",
            "2001:db8::1",
            "2002:7f00:1::",
            "3fff::1",
            "fc00::1",
            "fd12:3456::1",
            "fe80::1",
            "ff02::1",
        ).forEach { address ->
            assertTrue(!allows(address), address)
        }
    }

    @Test
    fun `DNS lookup rejects mixed public and private answers fail closed`() {
        val dns =
            PolicyEnforcingDns(
                Dns {
                    listOf(
                        InetAddress.getByName("93.184.216.34"),
                        InetAddress.getByName("127.0.0.1"),
                    )
                }
            )

        assertThrows(BlockedDestinationException::class.java) {
            dns.lookup("mixed.example")
        }
    }

    @Test
    fun `DNS lookup returns the exact validated public address set`() {
        val expected =
            listOf(
                InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("2606:4700:4700::1111"),
            )
        val dns = PolicyEnforcingDns(Dns { expected })

        assertEquals(expected, dns.lookup("public.example"))
    }

    @Test
    fun `HTTPS target rejects downgrade userinfo and fragments`() {
        assertNotNull(HttpsTargetPolicy.parse("https://example.com/path?q=1"))
        assertNull(HttpsTargetPolicy.parse("http://example.com/path"))
        assertNull(HttpsTargetPolicy.parse("https://user:pass@example.com/path"))
        assertNull(HttpsTargetPolicy.parse("https://example.com/path#fragment"))
        assertNull(HttpsTargetPolicy.parse("file:///etc/passwd"))
        assertNull(HttpsTargetPolicy.parse("https://127.0.0.1/private"))
        assertNull(HttpsTargetPolicy.parse("https://[::1]/private"))
        assertNotNull(HttpsTargetPolicy.parse("https://93.184.216.34/public"))
        assertNotNull(HttpsTargetPolicy.parse("https://example.com/search?q=faro&lang=es"))
        assertNull(HttpsTargetPolicy.parse("https://example.com/private?token=secret"))
        assertNull(HttpsTargetPolicy.parse("https://example.com/private?api_key=secret"))
        assertNull(HttpsTargetPolicy.parse("https://example.com/private?auth=secret"))
        assertNull(HttpsTargetPolicy.parse("https://example.com/private?AWSAccessKeyId=secret"))
        assertNull(HttpsTargetPolicy.parse("https://example.com/private?X-Amz-Signature=secret"))
        assertNull(HttpsTargetPolicy.parse("https://example.com/private?X-Goog-Credential=secret"))
        assertNull(HttpsTargetPolicy.parse("https://example.com/private?sig=secret"))
    }

    @Test
    fun `redirect resolution is bounded and rejects HTTPS downgrade`() {
        val current =
            requireNotNull(
                HttpsTargetPolicy.parse("https://example.com/a/b")
            )

        val safe =
            HttpsTargetPolicy.resolveRedirect(
                current = current,
                location = "../next",
                redirectCount = 0,
                maxRedirects = 3,
            )
        assertEquals("https://example.com/next", safe.toString())

        assertNull(
            HttpsTargetPolicy.resolveRedirect(
                current = current,
                location = "http://example.com/insecure",
                redirectCount = 0,
                maxRedirects = 3,
            )
        )
        assertNull(
            HttpsTargetPolicy.resolveRedirect(
                current = current,
                location = "https://cdn.example.com/file?Signature=credential",
                redirectCount = 0,
                maxRedirects = 3,
            )
        )
        assertNull(
            HttpsTargetPolicy.resolveRedirect(
                current = current,
                location = "https://example.com/too-far",
                redirectCount = 3,
                maxRedirects = 3,
            )
        )
    }

    private fun allows(literal: String): Boolean =
        PublicInternetAddressPolicy.allows(
            InetAddress.getByName(literal)
        )
}
