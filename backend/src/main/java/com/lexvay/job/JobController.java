package com.lexvay.job;

import com.lexvay.common.NotFoundException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    public record JobView(UUID id, JobType type, JobStatus status, int attempts, String error, OffsetDateTime createdAt) {
        static JobView of(Job job) {
            return new JobView(job.id(), job.type(), job.status(), job.attempts(), job.error(), job.createdAt());
        }
    }

    private final JobRepository jobs;

    public JobController(JobRepository jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/{id}")
    JobView get(@PathVariable UUID id) {
        return jobs.findById(id).map(JobView::of).orElseThrow(() -> new NotFoundException("Job not found"));
    }
}
