package com.lexvay.program;

import java.util.List;
import java.util.UUID;

public record Course(UUID id, UUID programId, String code, String title, List<CourseVersion> versions) {
}
