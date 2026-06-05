package com.example.filestorage.file;

import com.example.filestorage.storage.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class FileService {
    private final FileMetadataRepository fileMetadataRepository;
    private final StorageService storageService;

    public FileService(FileMetadataRepository fileMetadataRepository, StorageService storageService) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.storageService = storageService;
    }

    @Transactional
    public FileResponse upload(UUID ownerId, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        String safeFilename = sanitizeFilename(multipartFile.getOriginalFilename());
        String contentType = multipartFile.getContentType() == null ? "application/octet-stream" : multipartFile.getContentType();
        String objectKey = "users/%s/files/%s/%s".formatted(ownerId, UUID.randomUUID(), safeFilename);
        String sha256 = sha256(multipartFile);

        storageService.upload(objectKey, multipartFile);

        FileMetadata metadata = new FileMetadata(
                ownerId,
                safeFilename,
                contentType,
                multipartFile.getSize(),
                objectKey,
                sha256
        );
        return FileResponse.from(fileMetadataRepository.save(metadata));
    }

    @Transactional(readOnly = true)
    public FileResponse get(UUID ownerId, UUID fileId) {
        return FileResponse.from(findActiveOwnedFile(ownerId, fileId));
    }

    @Transactional(readOnly = true)
    public FileListResponse list(UUID ownerId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<FileMetadata> result = fileMetadataRepository.findAllByOwnerIdAndDeletedAtIsNull(
                ownerId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new FileListResponse(
                result.getContent().stream().map(FileResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> download(UUID ownerId, UUID fileId) {
        FileMetadata file = findActiveOwnedFile(ownerId, fileId);
        InputStream inputStream = storageService.download(file.getObjectKey());
        String encodedFilename = URLEncoder.encode(file.getOriginalFilename(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(encodedFilename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new InputStreamResource(inputStream));
    }

    @Transactional
    public void delete(UUID ownerId, UUID fileId) {
        FileMetadata file = findActiveOwnedFile(ownerId, fileId);
        file.softDelete();
        fileMetadataRepository.save(file);
    }

    private FileMetadata findActiveOwnedFile(UUID ownerId, UUID fileId) {
        return fileMetadataRepository.findByIdAndOwnerIdAndDeletedAtIsNull(fileId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("File not found"));
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        String sanitized = filename.replaceAll("[\\r\\n\\t]", "_").replaceAll("[/\\\\]", "_").trim();
        return sanitized.isBlank() ? "file" : sanitized;
    }

    private String sha256(MultipartFile multipartFile) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = multipartFile.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Could not calculate file checksum", e);
        }
    }
}
