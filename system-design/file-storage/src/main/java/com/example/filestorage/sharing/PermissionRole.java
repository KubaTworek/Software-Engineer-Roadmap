package com.example.filestorage.sharing;

public enum PermissionRole {
    VIEWER,
    EDITOR,
    OWNER;

    public boolean includes(PermissionRole required) {
        return rank(this) >= rank(required);
    }

    private static int rank(PermissionRole role) {
        return switch (role) {
            case VIEWER -> 1;
            case EDITOR -> 2;
            case OWNER -> 3;
        };
    }
}
