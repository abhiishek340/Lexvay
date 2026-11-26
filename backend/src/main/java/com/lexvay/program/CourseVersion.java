package com.lexvay.program;

import java.util.UUID;

public record CourseVersion(UUID id, UUID courseId, UUID programId, String catalogYear, boolean required, UUID supersedesId) {
}
