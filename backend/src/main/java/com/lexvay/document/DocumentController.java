package com.lexvay.document;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    /** One year, the longest a cache may keep a response; written out because the header order is part of the contract. */
    private static final String IMMUTABLE_PRIVATE = "private, max-age=31536000, immutable";

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

    /**
     * Pages are immutable (the database rejects changes), so the response may be cached for a year by the
     * browser that asked for it. {@code private} keeps shared caches out: the URL is only valid for the
     * tenant that owns the document. The ETag is the document hash plus the page number.
     */
    @GetMapping("/documents/{id}/pages/{pageNumber}")
    ResponseEntity<DocumentService.PageView> page(@PathVariable UUID id, @PathVariable int pageNumber) {
        var page = service.page(id, pageNumber);
        return immutable(ResponseEntity.ok(), "\"" + page.documentSha256() + "-" + pageNumber + "\"").body(page.view());
    }

    /**
     * The original uploaded file, shown inline by the browser ("Open original"). The bytes are addressed by
     * their hash, so they are cached like the pages; the ETag is the document hash. A matching
     * {@code If-None-Match} is answered with 304 before the file store is read.
     */
    @GetMapping("/documents/{id}/file")
    ResponseEntity<byte[]> file(@PathVariable UUID id, WebRequest request) {
        Document document = service.find(id);
        String etag = "\"" + document.sha256() + "\"";
        if (request.checkNotModified(etag)) {
            return immutable(ResponseEntity.status(HttpStatus.NOT_MODIFIED), etag).build();
        }
        return immutable(ResponseEntity.ok(), etag)
                .contentType(FileType.fromFilename(document.filename()).mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(document.filename(), StandardCharsets.UTF_8).build().toString())
                .body(service.content(document));
    }

    @GetMapping("/documents/{id}")
    DocumentService.DocumentView get(@PathVariable UUID id) {
        return service.get(id);
    }

    private static ResponseEntity.BodyBuilder immutable(ResponseEntity.BodyBuilder builder, String etag) {
        return builder.header(HttpHeaders.CACHE_CONTROL, IMMUTABLE_PRIVATE).eTag(etag);
    }
}
