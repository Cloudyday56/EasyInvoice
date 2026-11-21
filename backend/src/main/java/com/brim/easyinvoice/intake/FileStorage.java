package com.brim.easyinvoice.intake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** "Object storage" for the hackathon: a folder of files named by their SHA-256 hash. */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${app.storage-dir}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath();
    }

    /** Same content -> same name, so storing a file twice is harmless. */
    public String save(byte[] bytes, String sha256, String extension) {
        try {
            Files.createDirectories(root);
            Path target = root.resolve(sha256 + extension);
            if (!Files.exists(target)) Files.write(target, bytes);
            return target.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] read(String storagePath) {
        try {
            return Files.readAllBytes(Path.of(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean exists(String storagePath) {
        return storagePath != null && Files.exists(Path.of(storagePath));
    }

    public long size(String storagePath) {
        try {
            return Files.size(Path.of(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
