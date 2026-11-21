package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.SourceDocument;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, Long> {

    Optional<SourceDocument> findByIdempotencyKey(String idempotencyKey);

    default SourceDocument get(long id) {
        return findById(id).orElseThrow(() -> new NotFoundException("Document " + id + " not found"));
    }
}
