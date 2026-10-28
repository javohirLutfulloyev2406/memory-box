package memory.box.memorybox.saveditem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import memory.box.memorybox.processing.ProcessingStatus;
import memory.box.memorybox.user.User;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One link a user has asked Memory Box to remember. {@code url} is exactly what the
 * user submitted; {@code normalizedUrl} is the conservatively-normalized form used for
 * per-user duplicate detection (see {@code UrlNormalizer}). {@code search_vector} is a
 * database-generated column (see V2 migration) and intentionally has no Java field —
 * it is only ever queried through native SQL in the search layer.
 */
@Entity
@Table(name = "saved_items")
public class SavedItem {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "normalized_url", nullable = false)
    private String normalizedUrl;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "content_text")
    private String contentText;

    @Column(name = "source")
    private String source;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "user_note")
    private String userNote;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private ProcessingStatus processingStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedItem() {
        // JPA
    }

    public SavedItem(User user, String url, String normalizedUrl, String userNote) {
        this.user = user;
        this.url = url;
        this.normalizedUrl = normalizedUrl;
        this.userNote = userNote;
        this.savedAt = Instant.now();
        this.processingStatus = ProcessingStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.savedAt == null) {
            this.savedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void applyMetadata(String fetchedTitle, String fetchedDescription) {
        if (fetchedTitle != null && !fetchedTitle.isBlank()) {
            this.title = fetchedTitle;
        }
        if (fetchedDescription != null && !fetchedDescription.isBlank()) {
            this.description = fetchedDescription;
        }
    }

    public void markProcessingStatus(ProcessingStatus status) {
        this.processingStatus = status;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getUrl() {
        return url;
    }

    public String getNormalizedUrl() {
        return normalizedUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getUserNote() {
        return userNote;
    }

    public void setUserNote(String userNote) {
        this.userNote = userNote;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}