package com.example.webcrawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    private UrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UrlValidator();
    }

    @Test
    @DisplayName("Should validate correct HTTP and HTTPS URLs")
    void testValidSchemes() {
        assertTrue(validator.isValid("http://localhost:9000/page1.html"));
        assertTrue(validator.isValid("https://example.com/docs"));
        assertTrue(validator.isValid("http://127.0.0.1:8080/test"));
    }

    @Test
    @DisplayName("Should reject non-HTTP schemes and malformed URLs")
    void testInvalidSchemes() {
        assertFalse(validator.isValid(null));
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid("   "));
        assertFalse(validator.isValid("ftp://files.example.com/data.zip"));
        assertFalse(validator.isValid("mailto:user@example.com"));
        assertFalse(validator.isValid("javascript:void(0)"));
        assertFalse(validator.isValid("tel:+1234567890"));
        assertFalse(validator.isValid("not-a-url"));
        assertFalse(validator.isValid("http://")); // Missing host
    }

    @Test
    @DisplayName("Should reject binary and static media extensions")
    void testIgnoreStaticExtensions() {
        assertFalse(validator.isValid("http://example.com/image.png"));
        assertFalse(validator.isValid("http://example.com/doc.pdf"));
        assertFalse(validator.isValid("http://example.com/archive.zip"));
        assertFalse(validator.isValid("http://example.com/style.css"));
        assertFalse(validator.isValid("http://example.com/bundle.js"));
    }

    @Test
    @DisplayName("Should normalize URLs by stripping fragments and casing")
    void testNormalization() {
        assertEquals("http://example.com/page1.html",
                validator.normalize("http://EXAMPLE.COM/page1.html#section2"));

        assertEquals("http://example.com/page1.html?id=1",
                validator.normalize("http://example.com/page1.html?id=1#tab"));

        // Default port removal
        assertEquals("http://example.com/home",
                validator.normalize("http://example.com:80/home"));

        assertEquals("https://example.com/secure",
                validator.normalize("https://example.com:443/secure"));
    }

    @Test
    @DisplayName("Should enforce domain boundaries accurately")
    void testDomainAllowance() {
        // Allowed domain is localhost
        assertTrue(validator.isDomainAllowed("http://localhost:9000/page1.html", "localhost"));
        assertTrue(validator.isDomainAllowed("http://localhost:8080/api", "localhost:9000"));

        // Reject external domain
        assertFalse(validator.isDomainAllowed("https://external.com/page", "localhost"));

        // Subdomain matching
        assertTrue(validator.isDomainAllowed("https://docs.example.com/guide", "example.com"));
        assertFalse(validator.isDomainAllowed("https://other-site.com/guide", "example.com"));

        // No domain restriction configured
        assertTrue(validator.isDomainAllowed("https://anywhere.org/page", null));
        assertTrue(validator.isDomainAllowed("https://anywhere.org/page", ""));
    }

    @Test
    @DisplayName("Should extract host accurately")
    void testExtractHost() {
        assertEquals("localhost", validator.extractHost("http://localhost:9000/page1.html"));
        assertEquals("example.com", validator.extractHost("https://example.com/path"));
        assertNull(validator.extractHost("invalid-url"));
    }
}
