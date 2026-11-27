package com.lexvay.coverage;

import com.lexvay.common.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One read-only transaction and one statement per call (D-110). */
@Service
public class CoverageService {

    private final CoverageRepository coverage;

    public CoverageService(CoverageRepository coverage) {
        this.coverage = coverage;
    }

    @Transactional(readOnly = true)
    public CoverageMatrix matrix(UUID programId, String jurisdiction) {
        CoverageRepository.Found found = coverage.find(programId, jurisdiction).orElseThrow(() -> new NotFoundException("Program not found"));
        if (!found.target()) {
            throw new IllegalArgumentException("Jurisdiction " + jurisdiction + " is not a target of this program");
        }
        return found.matrix();
    }
}
