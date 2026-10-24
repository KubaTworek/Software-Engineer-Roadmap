package pl.jakubtworek.lld.filesystem.domain;

import java.util.Objects;

/**
 * Odmowa domenowa z kontekstem, bez parsowania komunikatu. path to operand
 * operacji (np. pełny cel copy), problemPath wskazuje brakujący prefiks lub plik
 * blokujący przejście. Błędna składnia FsPath pozostaje błędem danych wejścia.
 */
public final class FileSystemException extends IllegalArgumentException {

    public enum Reason {

        NOT_FOUND,
        NOT_DIRECTORY,
        NOT_FILE,
        ALREADY_EXISTS,
        ROOT_PROTECTED,
        DIRECTORY_NOT_EMPTY,
        INVALID_MOVE,
        INVALID_COPY
    }

    public enum Operation {

        STAT,
        READ,
        LIST,
        FIND,
        MKDIR,
        CREATE_FILE,
        WRITE,
        DELETE,
        MOVE,
        COPY
    }

    private final Reason reason;
    private final Operation operation;
    private final FsPath path;
    private final FsPath problemPath;

    public FileSystemException(Reason reason, Operation operation, FsPath path, FsPath problemPath) {
        super(reason + " operation=" + operation + " path=" + path + " problem=" + problemPath);
        this.reason = Objects.requireNonNull(reason);
        this.operation = Objects.requireNonNull(operation);
        this.path = Objects.requireNonNull(path);
        this.problemPath = Objects.requireNonNull(problemPath);
    }

    public Reason reason() {
        return reason;
    }

    public Operation operation() {
        return operation;
    }

    public FsPath path() {
        return path;
    }

    public FsPath problemPath() {
        return problemPath;
    }
}
