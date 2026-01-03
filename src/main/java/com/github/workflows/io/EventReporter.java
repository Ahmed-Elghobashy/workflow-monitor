package com.github.workflows.io;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Formats and reports workflow events to stdout.
 * Each event is reported as a single line with a consistent format.
 */
public class EventReporter {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final String COLOR_RESET = "\u001B[0m";
    private static final String COLOR_BLUE = "\u001B[34m";
    private static final String COLOR_CYAN = "\u001B[36m";
    private static final String COLOR_GREEN = "\u001B[32m";
    private static final String COLOR_YELLOW = "\u001B[33m";
    private static final String COLOR_RED = "\u001B[31m";
    private static final String COLOR_MAGENTA = "\u001B[35m";
    
    private final Consumer<String> lineWriter;

    public EventReporter() {
        this(System.out::println);
    }

    public EventReporter(Consumer<String> lineWriter) {
        this.lineWriter = lineWriter != null ? lineWriter : System.out::println;
    }
    
    /**
     * Report a workflow run queued event.
     * Format: WORKFLOW_QUEUED|runId=<id>|workflow=<name>|branch=<branch>|commit=<sha>|time=<iso-timestamp>
     */
    public void reportWorkflowQueued(long runId, String workflowName, 
                                     String branch, String commitSha, 
                                     Instant timestamp) {
        logEvent(
            timestamp,
            "WORKFLOW QUEUED",
            COLOR_BLUE,
            String.format(
                "Run #%d \"%s\" (branch: %s, commit: %s)",
                runId,
                escape(workflowName),
                escape(branch),
                commitSha != null ? commitSha : "unknown"
            )
        );
    }

    /**
     * Report a job started event.
     * Format: JOB_STARTED|runId=<id>|jobId=<id>|job=<name>|time=<iso-timestamp>
     */
    public void reportJobStarted(long runId, long jobId, String jobName, 
                                 Instant timestamp) {
        logEvent(
            timestamp,
            "JOB STARTED",
            COLOR_CYAN,
            String.format(
                "Run #%d Job #%d \"%s\"",
                runId,
                jobId,
                escape(jobName)
            )
        );
    }

    /**
     * Report a job finished event.
     * Format: JOB_FINISHED|runId=<id>|jobId=<id>|job=<name>|conclusion=<success|failure|...>|start=<iso-timestamp>|end=<iso-timestamp>|duration=<seconds>
     */
    public void reportJobFinished(long runId, long jobId, String jobName, 
                                  String conclusion, Instant startTime, 
                                  Instant endTime) {
        long durationSeconds = calculateDuration(startTime, endTime);
        logEvent(
            endTime,
            "JOB FINISHED",
            colorForConclusion(conclusion),
            String.format(
                "Run #%d Job #%d \"%s\" => %s (%ds)",
                runId,
                jobId,
                escape(jobName),
                (conclusion != null ? conclusion : "unknown").toUpperCase(),
                durationSeconds
            )
        );
    }

    /**
     * Report a step started event.
     * Format: STEP_STARTED|runId=<id>|jobId=<id>|step=<name>|time=<iso-timestamp>
     */
    public void reportStepStarted(long runId, long jobId, String stepName, 
                                  Instant timestamp) {
        logEvent(
            timestamp,
            "STEP STARTED",
            COLOR_MAGENTA,
            String.format(
                "Run #%d Job #%d step \"%s\"",
                runId,
                jobId,
                escape(stepName)
            )
        );
    }

    /**
     * Report a step completed event (success or failure).
     * Format: STEP_SUCCESS|runId=<id>|jobId=<id>|step=<name>|start=<iso-timestamp>|end=<iso-timestamp>|duration=<seconds>
     * or: STEP_FAILURE|runId=<id>|jobId=<id>|step=<name>|start=<iso-timestamp>|end=<iso-timestamp>|duration=<seconds>
     */
    public void reportStepCompleted(long runId, long jobId, String stepName, 
                                    String conclusion, Instant startTime, 
                                    Instant endTime) {
        long durationSeconds = calculateDuration(startTime, endTime);
        String conclusionUpper = conclusion != null ? conclusion.toUpperCase() : "UNKNOWN";
        logEvent(
            endTime,
            "STEP " + conclusionUpper,
            colorForConclusion(conclusion),
            String.format(
                "Run #%d Job #%d step \"%s\" (%ds)",
                runId,
                jobId,
                escape(stepName),
                durationSeconds
            )
        );
    }

    /**
     * Calculate duration in seconds between two timestamps.
     */
    private long calculateDuration(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0;
        }
        return Duration.between(start, end).getSeconds();
    }

    /**
     * Escape special characters in field values to prevent format issues.
     * Replaces pipe (|) and newline characters.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "_").replace("\n", " ").replace("\r", " ");
    }

    private void logEvent(Instant timestamp, String eventName, String color, String details) {
        String timePart = timestamp != null ? TIMESTAMP_FORMATTER.format(timestamp) : "--";
        String formatted = String.format("[%s] %-16s %s", timePart, eventName, details);
        lineWriter.accept(color + formatted + COLOR_RESET);
    }

    private String colorForConclusion(String conclusion) {
        if (conclusion == null) {
            return COLOR_YELLOW;
        }
        switch (conclusion.toLowerCase()) {
            case "success":
                return COLOR_GREEN;
            case "failure":
            case "failed":
            case "cancelled":
            case "timed_out":
                return COLOR_RED;
            default:
                return COLOR_YELLOW;
        }
    }
}
