package com.receipttrust.common.storage;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "application/pdf");

    private final Path receiptDir;
    private final Path profileDir;

    public FileStorageService(StorageProperties properties) {
        this.receiptDir = Paths.get(properties.getReceiptDir()).toAbsolutePath().normalize();
        this.profileDir = Paths.get(properties.getProfileDir()).toAbsolutePath().normalize();
    }

    public StoredFile storeReceiptImage(MultipartFile file) {
        validateReceiptType(file);
        return store(receiptDir, file);
    }

    public StoredFile storeProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiExceptions.ValidationException("File is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png"))) {
            throw new ApiExceptions.ValidationException("Profile image must be JPG or PNG");
        }
        return store(profileDir, file);
    }

    public InputStream openReceiptImage(String relativePath) {
        return open(receiptDir, relativePath);
    }

    private void validateReceiptType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiExceptions.ValidationException("Receipt image is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new ApiExceptions.ValidationException("Receipt must be JPG, PNG, or PDF");
        }
    }

    private StoredFile store(Path dir, MultipartFile file) {
        try {
            Files.createDirectories(dir);
            String ext = extensionFor(file.getContentType());
            String filename = UUID.randomUUID() + ext;
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) {
                throw new ApiExceptions.ValidationException("Invalid file path");
            }
            file.transferTo(target);
            return new StoredFile(filename, file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    private InputStream open(Path dir, String relativePath) {
        try {
            Path target = dir.resolve(relativePath).normalize();
            if (!target.startsWith(dir) || !Files.exists(target)) {
                throw new ApiExceptions.ResourceNotFoundException("File not found");
            }
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new ApiExceptions.ResourceNotFoundException("File not found");
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }
}
