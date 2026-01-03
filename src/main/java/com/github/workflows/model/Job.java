package com.github.workflows.model;

import java.time.Instant;

/**
 * Represents a job within a workflow run.
 */
public class Job {
    private final long id;
    private final String name;
    private final String status;
    private final String conclusion;
    private final Instant startedAt;
    private final Instant completedAt;

    public Job(long id, String name, String status, String conclusion, 
               Instant startedAt, Instant completedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.conclusion = conclusion;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getConclusion() {
        return conclusion;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}

