package com.al.lightq.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompressionHubTest {

    @Test
    void compressAndDecompress() {
        String original = "Hello World".repeat(100);
        String compressed = CompressionHub.compress(original);

        assertNotNull(compressed);
        assertFalse(compressed.equals(original));
        assertTrue(compressed.length() < original.length());

        String decompressed = CompressionHub.decompress(compressed);
        assertEquals(original, decompressed);
    }

    @Test
    void compressNullOrEmpty() {
        assertEquals(null, CompressionHub.compress(null));
        assertEquals("", CompressionHub.compress(""));
    }

    @Test
    void decompressNullOrEmpty() {
        assertEquals(null, CompressionHub.decompress(null));
        assertEquals("", CompressionHub.decompress(""));
    }
}
