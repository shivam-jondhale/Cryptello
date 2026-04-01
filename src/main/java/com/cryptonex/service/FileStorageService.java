package com.cryptonex.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

public interface FileStorageService {
    String saveFile(MultipartFile file) throws IOException;
    Path getFilePath(String fileName);
    void deleteFile(String fileName) throws IOException;
}
