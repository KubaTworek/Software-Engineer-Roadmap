package com.example.filestorage.folder;

import com.example.filestorage.file.FileResponse;
import java.util.List;

public record FolderChildrenResponse(
        List<FolderResponse> folders,
        List<FileResponse> files,
        int page,
        int size,
        long totalFolders,
        long totalFiles,
        int totalFolderPages,
        int totalFilePages
) {}
