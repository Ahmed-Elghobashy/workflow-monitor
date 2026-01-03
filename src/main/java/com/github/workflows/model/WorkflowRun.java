package com.github.workflows.model;

import java.time.Instant;

/**
 * Represents a GitHub Actions workflow run.
 */
public class WorkflowRun {
    private final long id;
    private final String name;
    private final String branch;
    private final String commitSha;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String status;
    private final String conclusion;

    public WorkflowRun(long id, String name, String branch, String commitSha,
                       Instant createdAt, Instant updatedAt, String status, String conclusion) {
        this.id = id;
        this.name = name;
        this.branch = branch;
        this.commitSha = commitSha;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
        this.conclusion = conclusion;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getConclusion() {
        return conclusion;
    }
}
