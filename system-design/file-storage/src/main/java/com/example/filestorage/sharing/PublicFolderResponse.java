package com.example.filestorage.sharing;

import com.example.filestorage.folder.FolderChildrenResponse;
import com.example.filestorage.folder.FolderResponse;

public record PublicFolderResponse(FolderResponse folder, FolderChildrenResponse children) {}
