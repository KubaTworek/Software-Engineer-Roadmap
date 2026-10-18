package pl.jakubtworek.chatsystem.media;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Minimalna wykonywalna granica skanera; produkcyjny adapter używa AV/CDR i sandboxu. */
@Component
public class AttachmentContentPolicy {
    public Inspection inspect(String declaredMime, byte[] content) {
        boolean valid = switch (declaredMime) {
            case "application/pdf" -> startsWith(content, "%PDF".getBytes(StandardCharsets.US_ASCII));
            case "image/png" -> content.length >= 8 && content[0] == (byte) 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G';
            case "image/jpeg" -> content.length >= 3 && content[0] == (byte) 0xff && content[1] == (byte) 0xd8 && content[2] == (byte) 0xff;
            default -> declaredMime.startsWith("text/") && java.util.Arrays.stream(toUnsigned(content)).noneMatch(b -> b == 0);
        };
        return new Inspection(valid, sha256(content));
    }
    private int[] toUnsigned(byte[] bytes) { int[] out = new int[bytes.length]; for(int i=0;i<bytes.length;i++) out[i]=bytes[i]&0xff; return out; }
    private boolean startsWith(byte[] value, byte[] prefix) { return value.length >= prefix.length && java.util.Arrays.equals(prefix, java.util.Arrays.copyOf(value, prefix.length)); }
    private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public record Inspection(boolean accepted, String checksum) {}
}
