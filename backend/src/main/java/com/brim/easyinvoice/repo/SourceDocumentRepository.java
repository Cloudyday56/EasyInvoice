package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class SourceDocumentRepository {

    private static final RowMapper<SourceDocument> MAPPER = (rs, i) -> new SourceDocument(
            rs.getLong("id"),
            rs.getString("channel"),
            rs.getString("email_id"),
            rs.getString("file_name"),
            rs.getString("content_type"),
            (Long) rs.getObject("size_bytes"),
            rs.getString("sha256"),
            rs.getString("storage_path"),
            rs.getString("sender"),
            rs.getString("subject"),
            rs.getString("body"),
            rs.getObject("received_at", OffsetDateTime.class),
            rs.getString("uploaded_by"));

    private final JdbcTemplate jdbc;

    public SourceDocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SourceDocument get(long id) {
        return jdbc.query("SELECT * FROM source_documents WHERE id = ?", MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Document " + id + " not found"));
    }

    public Optional<SourceDocument> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM source_documents WHERE idempotency_key = ?", MAPPER, key)
                .stream().findFirst();
    }

    public long insert(String channel, String emailId, String idempotencyKey, String fileName, String contentType,
                       Long sizeBytes, String sha256, String storagePath, String sender, String subject,
                       String body, OffsetDateTime receivedAt, String uploadedBy) {
        return jdbc.queryForObject("""
                INSERT INTO source_documents (channel, email_id, idempotency_key, file_name, content_type,
                    size_bytes, sha256, storage_path, sender, subject, body, received_at, uploaded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, channel, emailId, idempotencyKey, fileName, contentType, sizeBytes, sha256,
                storagePath, sender, subject, body, receivedAt, uploadedBy);
    }
}
