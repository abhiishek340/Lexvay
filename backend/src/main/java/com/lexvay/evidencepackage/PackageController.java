package com.lexvay.evidencepackage;

import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PackageController {

    private static final MediaType ZIP = new MediaType("application", "zip");

    private final PackageService service;

    public PackageController(PackageService service) {
        this.service = service;
    }

    /** Long operation: 202 with the package id; the ZIP is built by a job. */
    @PostMapping("/determinations/{id}/package")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PackageService.RequestResult request(@PathVariable UUID id) {
        return service.request(id);
    }

    @GetMapping("/packages/{id}")
    PackageService.PackageView get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** The ZIP, streamed from the file store without being held in memory. */
    @GetMapping("/packages/{id}/file")
    ResponseEntity<InputStreamResource> file(@PathVariable UUID id) {
        PackageService.Download download = service.download(id);
        return ResponseEntity.ok()
                .contentType(ZIP)
                .contentLength(download.sizeBytes())
                .eTag("\"" + download.sha256() + "\"")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.filename()).build().toString())
                .body(new InputStreamResource(download.content()));
    }
}
