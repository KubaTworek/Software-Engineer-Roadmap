package com.example.filestorage.file;

import com.example.filestorage.config.CurrentUser;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public FileResponse upload(CurrentUser currentUser, @RequestParam("file") MultipartFile file) {
        return fileService.upload(currentUser.id(), file);
    }

    @GetMapping
    public FileListResponse list(CurrentUser currentUser,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return fileService.list(currentUser.id(), page, size);
    }

    @GetMapping("/{fileId}")
    public FileResponse get(CurrentUser currentUser, @PathVariable UUID fileId) {
        return fileService.get(currentUser.id(), fileId);
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(CurrentUser currentUser, @PathVariable UUID fileId) {
        return fileService.download(currentUser.id(), fileId);
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(CurrentUser currentUser, @PathVariable UUID fileId) {
        fileService.delete(currentUser.id(), fileId);
        return ResponseEntity.noContent().build();
    }
}
