package com.receipttrust.ocr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OcrTermRepository extends JpaRepository<OcrTerm, Long> {

    Optional<OcrTerm> findByKindAndNormalized(OcrTerm.Kind kind, String normalized);

    List<OcrTerm> findByKind(OcrTerm.Kind kind);
}
