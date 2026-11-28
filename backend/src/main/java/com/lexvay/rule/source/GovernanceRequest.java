package com.lexvay.rule.source;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The whole governance of a source, as an administrator states it (RG-2, D-566). Nothing is merged: what is not listed
 * is removed. Hosts are host names or IP literals without port, compared in lower case.
 */
public record GovernanceRequest(
        @NotNull AuthorityClass authorityClass,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 255) @Pattern(regexp = "^([A-Za-z0-9.-]+|\\[[0-9A-Fa-f:.]+\\])$") String> allowedHosts,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) @Pattern(regexp = "^/\\S*$") String> allowedPathPrefixes,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 200) String> expectedIdentifiers,
        @NotNull AccessStatus accessStatus,
        @Size(max = 2000) String accessNote,
        UUID ownerUserId) {

    SourceGovernance governance() {
        return new SourceGovernance(authorityClass, allowedHosts.stream().map(host -> host.toLowerCase(Locale.ROOT)).distinct().toList(),
                allowedPathPrefixes.stream().distinct().toList(), expectedIdentifiers.stream().map(String::strip).distinct().toList(),
                accessStatus, accessNote == null || accessNote.isBlank() ? null : accessNote.strip(), ownerUserId);
    }
}
