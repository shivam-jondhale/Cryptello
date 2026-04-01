package com.cryptonex.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageServiceImpl() throws IOException {
        // Define upload directory
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();

        // Create the directory if it doesn't exist
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new IOException("Could not create the directory to store files.", ex);
        }
    }

    @Override
    public String saveFile(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.contains("..")) {
            throw new IOException("Invalid file name: " + originalFileName);
        }

        // Create a sanitized file name to avoid conflicts
        String sanitizedFileName = System.currentTimeMillis() + "_" + originalFileName.replaceAll("[^a-zA-Z0-9.]", "_");
        Path targetLocation = this.fileStorageLocation.resolve(sanitizedFileName);

        // Save the file
        Files.copy(file.getInputStream(), targetLocation);

        return sanitizedFileName; // Returning the filename to save in DB
    }

    @Override
    public Path getFilePath(String fileName) {
        // Retrieve the file path
        return this.fileStorageLocation.resolve(fileName).normalize();
    }

    @Override
    public void deleteFile(String fileName) throws IOException {
        Path filePath = getFilePath(fileName);

        if (!Files.exists(filePath)) {
            // logger.warn("File not found: {}", fileName);
            return;
        }

        Files.delete(filePath);
    }
}
