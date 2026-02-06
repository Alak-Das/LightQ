package com.al.lightq.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompressionHub {

    private static final Logger logger = LoggerFactory.getLogger(CompressionHub.class);

    public static String compress(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
            gzip.finish(); // Ensure all data is flushed to underlying stream before converting
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            logger.error("Failed to compress content", e);
            throw new RuntimeException("Compression failed", e);
        }
    }

    public static String decompress(String compressedContent) {
        if (compressedContent == null || compressedContent.isEmpty()) {
            return compressedContent;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(compressedContent));
                GZIPInputStream gzip = new GZIPInputStream(bis)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException e) {
            logger.error("Failed to decompress content", e);
            // Fallback or restart? If decompression fails, it's data corruption or not
            // compressed
            // We assume caller honors 'compressed' flag.
            throw new RuntimeException("Decompression failed", e);
        }
    }
}
