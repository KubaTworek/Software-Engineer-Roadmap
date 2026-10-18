package pl.jakubtworek.chatsystem.media;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.UUID;

/**
 * Asynchroniczna granica skanowania. Zapis do quarantine nie nadaje plikowi
 * prawa do pobrania; dopiero zaakceptowany skan promuje obiekt do clean.
 */
@Service
public class AttachmentScanWorker {
    private final AttachmentRepository attachments;
    private final AttachmentContentPolicy contentPolicy;
    private final ObjectStorage storage;
    private final boolean schedulingEnabled;

    public AttachmentScanWorker(AttachmentRepository attachments, AttachmentContentPolicy contentPolicy,
                                ObjectStorage storage,
                                @Value("${app.media.scanner-enabled:true}") boolean schedulingEnabled) {
        this.attachments = attachments;
        this.contentPolicy = contentPolicy;
        this.storage = storage;
        this.schedulingEnabled = schedulingEnabled;
    }

    @Scheduled(fixedDelayString = "${app.media.scan-delay-ms:1000}")
    public void scanBatch() {
        if (!schedulingEnabled) return;
        attachments.findTop50ByStatusOrderByUploadedAtAsc(AttachmentStatus.SCANNING)
                .forEach(attachment -> scan(attachment.getId()));
    }

    @Transactional
    public AttachmentStatus scan(UUID attachmentId) {
        Attachment attachment = attachments.findById(attachmentId).orElseThrow();
        if (attachment.getStatus() != AttachmentStatus.SCANNING) return attachment.getStatus();
        try {
            byte[] content = storage.readQuarantine(attachment.getStorageKey());
            AttachmentContentPolicy.Inspection inspection = contentPolicy.inspect(attachment.getMimeType(), content);
            if (!inspection.accepted()) {
                storage.deleteQuarantine(attachment.getStorageKey());
                attachment.reject();
            } else {
                // Promotion poprzedza READY. Awaria pozostawia stan SCANNING,
                // który worker może ponowić; klient nigdy nie dostaje linku za wcześnie.
                storage.promoteToClean(attachment.getStorageKey());
                attachment.markReady(inspection.checksum());
            }
            attachments.save(attachment);
            return attachment.getStatus();
        } catch (IOException ex) {
            // Awaria storage jest retryowalna, więc nie zamieniamy jej w REJECTED.
            throw new IllegalStateException("Attachment scan could not access object storage", ex);
        }
    }
}
