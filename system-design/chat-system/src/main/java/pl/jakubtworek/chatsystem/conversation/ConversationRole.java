package pl.jakubtworek.chatsystem.conversation;

public enum ConversationRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canManageRoles() {
        return this == OWNER;
    }
}
