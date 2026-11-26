package com.lexvay.document;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    /** Long operation: returns 202 with the ingest job id. */
    @PostMapping(path = "/programs/{programId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMIN')")
    DocumentService.UploadResult upload(@PathVariable UUID programId,
                                        @RequestPart("file") MultipartFile file,
                                        @RequestParam DocumentKind kind,
                                        @RequestParam(required = false) UUID courseVersionId) {
        return service.upload(programId, file, kind, courseVersionId);
    }

    @GetMapping("/documents/{id}")
    DocumentService.DocumentView get(@PathVariable UUID id) {
        return service.get(id);
    }
}
