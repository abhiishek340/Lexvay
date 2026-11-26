package com.lexvay.search;

import com.lexvay.document.DocumentKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/programs/{programId}/search")
@Validated
public class SearchController {

    static final int MAX_LIMIT = 50;
    static final int MAX_QUERY_LENGTH = 500;

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    List<SearchHit> search(@PathVariable UUID programId,
                           @RequestParam @NotBlank @Size(max = MAX_QUERY_LENGTH) String q,
                           @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_LIMIT) int limit,
                           @RequestParam(required = false) DocumentKind kind,
                           @RequestParam(required = false) UUID courseVersionId) {
        return service.search(programId, q, kind, courseVersionId, limit);
    }
}
