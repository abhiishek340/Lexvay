package com.lexvay.pathway;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why a group no longer describes the program; the group and its reason stay on record. */
public record RetirePathwayGroupRequest(@NotBlank @Size(max = 500) String reason) {
}
