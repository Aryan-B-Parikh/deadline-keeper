package com.deadlinekeeper.security;

import com.deadlinekeeper.exception.ValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Component
public class ImageValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_WIDTH = 4096;
    private static final int MAX_HEIGHT = 4096;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    // Magic bytes for common image formats
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
        "image/png", new byte[]{(byte)0x89, 0x50, 0x4E, 0x47}, // \x89PNG
        "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF},
        "image/gif", new byte[]{0x47, 0x49, 0x46, 0x38}, // GIF8
        "image/webp", new byte[]{0x52, 0x49, 0x46, 0x46}  // RIFF (WebP starts with RIFF)
    );

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("No file uploaded");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("File too large: " + file.getSize() + " bytes (max: " + MAX_FILE_SIZE + ")");
        }

        // Check claimed content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ValidationException("Unsupported image type: " + contentType + ". Allowed: PNG, JPEG, GIF, WebP");
        }

        try {
            byte[] bytes = file.getBytes();

            // Validate magic bytes
            String detectedType = detectMimeType(bytes);
            if (detectedType == null) {
                throw new ValidationException("File content does not match any supported image format");
            }

            if (!detectedType.equals(contentType.toLowerCase()) && 
                !contentType.toLowerCase().startsWith(detectedType)) {
                throw new ValidationException("Content type mismatch: claimed " + contentType + " but file is " + detectedType);
            }

            // Validate image dimensions (decompression bomb check)
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ValidationException("Could not read image file");
            }

            if (image.getWidth() > MAX_WIDTH || image.getHeight() > MAX_HEIGHT) {
                throw new ValidationException("Image too large: " + image.getWidth() + "x" + image.getHeight() + 
                    " (max: " + MAX_WIDTH + "x" + MAX_HEIGHT + ")");
            }

        } catch (ValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new ValidationException("Failed to read image: " + e.getMessage());
        }
    }

    private String detectMimeType(byte[] data) {
        if (data == null || data.length < 4) return null;

        for (Map.Entry<String, byte[]> entry : MAGIC_BYTES.entrySet()) {
            byte[] magic = entry.getValue();
            if (data.length >= magic.length) {
                boolean match = true;
                for (int i = 0; i < magic.length; i++) {
                    if (data[i] != magic[i]) {
                        match = false;
                        break;
                    }
                }
                if (match) return entry.getKey();
            }
        }
        return null;
    }
}
