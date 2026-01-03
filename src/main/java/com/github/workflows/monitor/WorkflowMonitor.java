package com.github.workflows.monitor;

import com.github.workflows.api.GitHubApiClient;
import com.github.workflows.io.EventReporter;
import com.github.workflows.io.StateManager;
import com.github.workflows.model.Job;
import com.github.workflows.model.Step;
import com.github.workflows.model.WorkflowRun;
import com.github.workflows.ui.ConsoleSpinner;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WorkflowMonitor {
    static {
        Logger baseLogger = Logger.getLogger("org.kohsuke.github");
        baseLogger.setLevel(Level.SEVERE);
        for (Handler handler : baseLogger.getHandlers()) {
            handler.setLevel(Level.SEVERE);
        }
        Logger enumLogger = Logger.getLogger("org.kohsuke.github.internal.EnumUtils");
        enumLogger.setLevel(Level.SEVERE);
        for (Handler handler : enumLogger.getHandlers()) {
            handler.setLevel(Level.SEVERE);
        }
    }

    private final String repository;
    private final String token;
    private final long pollIntervalSeconds = 2;
    
    // AtomicBoolean may be needed for thread-safe access from multiple threads
    // TODO: do we really need it?
    // TODO: clean up the CLI (proper command name instead of jar, README instructions, folder naming).
    // TODO: ensure appropriate permissions for reading/writing the persisted state file.
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ScheduledExecutorService executor;
    
    private GitHubApiClient apiClient;
    private final StateManager stateManager;
    private final EventReporter eventReporter;
    private final ConsoleSpinner monitoringSpinner;
    private volatile Instant lastProcessedTime;
    private volatile Instant resumeThreshold;
    private final DateTimeFormatter logTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());
    private final Map<Long, RunProgress> runProgress = new ConcurrentHashMap<>();

    public WorkflowMonitor(String repository, String token) {
        this.repository = repository;
        this.token = token;
        this.stateManager = new StateManager(repository);
        this.monitoringSpinner = new ConsoleSpinner("Monitoring workflows...");
        this.eventReporter = new EventReporter(
            line -> this.monitoringSpinner.runBlocking(() -> System.out.println(line))
        );
    }

    public void start() throws IOException {
        isRunning.set(true);
        
        log("Starting workflow monitor for repository: " + repository);
        log("Polling interval: " + pollIntervalSeconds + " seconds");
        
        // Initialize GitHub API client
        log("Connecting to GitHub API...");
        apiClient = new GitHubApiClient(token, repository);
        log("Connected successfully!");
        
        // Load previous state
        Instant storedLastRunTime = stateManager.loadLastRunTime();
        lastProcessedTime = storedLastRunTime;
        resumeThreshold = storedLastRunTime;
        if (storedLastRunTime != null) {
            log("Resuming from last run time: " + formatInstant(storedLastRunTime));
        } else {
            log("First run - will report all workflow runs");
        }
        
        // Start monitoring loop
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleWithFixedDelay(
            () -> {
                if (!isRunning.get()) {
                    return;
                }
                try {
                    monitorWorkflows();
                } catch (Exception e) {
                    handleConnectionLoss(e);
                }
            },
            0,
            pollIntervalSeconds,
            TimeUnit.SECONDS
        );
        monitoringSpinner.start();
        
        // Keep the main thread alive
        try {
            while (isRunning.get()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            monitoringSpinner.stop();
        }
    }

    public void stop() {
        isRunning.set(false);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        monitoringSpinner.stop("Monitoring stopped.");
        // Always save state before shutting down
        try {
            stateManager.saveLastRunTime(Instant.now());
        } catch (Exception e) {
            log("Warning: Failed to save state during shutdown: " + e.getMessage());
        }
    }

    private void monitorWorkflows() throws IOException {
        Instant since = lastProcessedTime;
        List<WorkflowRun> workflowRuns = apiClient.getWorkflowRunsSince(since);
        if (workflowRuns.isEmpty()) {
            return;
        }
        log("Found " + workflowRuns.size() + " workflow run(s)");
        
        Instant latestProcessedTime = since != null ? since : Instant.EPOCH;
        
        for (WorkflowRun run : workflowRuns) {
            processRun(run);
            Instant referenceTime = run.getUpdatedAt() != null ? run.getUpdatedAt() : run.getCreatedAt();
            if (referenceTime != null && referenceTime.isAfter(latestProcessedTime)) {
                latestProcessedTime = referenceTime;
            }
        }
        
        try {
            lastProcessedTime = latestProcessedTime;
            stateManager.saveLastRunTime(latestProcessedTime);
            resumeThreshold = null;
        } catch (Exception e) {
            log("Warning: Failed to save state after processing: " + e.getMessage());
        }
    }

    private void processRun(WorkflowRun run) throws IOException {
        long runId = run.getId();
        RunProgress progress = runProgress.computeIfAbsent(runId, id -> new RunProgress());
        
        if (!progress.workflowQueued) {
            progress.workflowQueued = true;
            if (isAfterThreshold(run.getCreatedAt())) {
                eventReporter.reportWorkflowQueued(
                    runId,
                    run.getName(),
                    run.getBranch(),
                    run.getCommitSha(),
                    run.getCreatedAt()
                );
            }
        }
        
        try {
            List<Job> jobs = apiClient.getJobsForRun(runId)
                .stream()
                .filter(this::shouldProcessJob)
                .collect(Collectors.toList());
            for (Job job : jobs) {
                long jobId = job.getId();
                
                if (job.getStartedAt() != null && isAfterThreshold(job.getStartedAt())
                    && progress.jobStarted.add(jobId)) {
                    eventReporter.reportJobStarted(
                        runId,
                        jobId,
                        job.getName(),
                        job.getStartedAt()
                    );
                }
                
                try {
                    List<Step> steps = apiClient.getStepsForJob(jobId)
                        .stream()
                        .filter(this::shouldProcessStep)
                        .collect(Collectors.toList());
                    for (Step step : steps) {
                        if (step.getStartedAt() != null && isAfterThreshold(step.getStartedAt())) {
                            String startKey = stepKey(jobId, step.getName(), step.getStartedAt(), "start");
                            if (progress.stepStarted.add(startKey)) {
                                eventReporter.reportStepStarted(
                                    runId,
                                    jobId,
                                    step.getName(),
                                    step.getStartedAt()
                                );
                            }
                        }
                        
                        if (step.getStartedAt() != null && step.getCompletedAt() != null
                            && isAfterThreshold(step.getCompletedAt())) {
                            String completionKey = stepKey(jobId, step.getName(), step.getCompletedAt(), "end");
                            if (progress.stepCompleted.add(completionKey)) {
                                eventReporter.reportStepCompleted(
                                    runId,
                                    jobId,
                                    step.getName(),
                                    step.getConclusion(),
                                    step.getStartedAt(),
                                    step.getCompletedAt()
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    log("Warning: Failed to fetch steps for job " + jobId + ": " + e.getMessage());
                }
                
                if (job.getCompletedAt() != null && job.getStartedAt() != null
                    && isAfterThreshold(job.getCompletedAt()) && progress.jobFinished.add(jobId)) {
                    eventReporter.reportJobFinished(
                        runId,
                        jobId,
                        job.getName(),
                        job.getConclusion(),
                        job.getStartedAt(),
                        job.getCompletedAt()
                    );
                }
            }
        } catch (Exception e) {
            log("Warning: Failed to fetch jobs for run " + runId + ": " + e.getMessage());
        }
        
        if ("completed".equalsIgnoreCase(run.getStatus()) || run.getConclusion() != null) {
            runProgress.remove(runId);
        }
    }

    private void log(String message) {
        monitoringSpinner.runBlocking(() -> System.err.println(message));
    }

    private void logStackTrace(Throwable throwable) {
        monitoringSpinner.runBlocking(throwable::printStackTrace);
    }

    private void handleConnectionLoss(Exception exception) {
        if (!isRunning.compareAndSet(true, false)) {
            return;
        }
        log("Connection to GitHub lost. Stopping monitoring: " + exception.getMessage());
        logStackTrace(exception);
        if (executor != null) {
            executor.shutdownNow();
        }
        monitoringSpinner.stop("Monitoring stopped: connection lost.");
        try {
            stateManager.saveLastRunTime(Instant.now());
        } catch (Exception e) {
            log("Warning: Failed to save state during shutdown: " + e.getMessage());
        }
    }

    private String formatInstant(Instant instant) {
        return logTimeFormatter.format(instant);
    }

    private String stepKey(long jobId, String stepName, Instant timestamp, String phase) {
        String namePart = stepName != null ? stepName : "step";
        String timePart = timestamp != null ? timestamp.toString() : "unknown";
        return jobId + "|" + namePart + "|" + timePart + "|" + phase;
    }

    private boolean isAfterThreshold(Instant timestamp) {
        if (timestamp == null) {
            return resumeThreshold == null;
        }
        Instant threshold = resumeThreshold;
        return threshold == null || timestamp.isAfter(threshold);
    }

    private boolean shouldProcessJob(Job job) {
        if (resumeThreshold == null) {
            return true;
        }
        Instant reference = job.getCompletedAt() != null ? job.getCompletedAt() : job.getStartedAt();
        return reference != null && reference.isAfter(resumeThreshold);
    }

    private boolean shouldProcessStep(Step step) {
        if (resumeThreshold == null) {
            return true;
        }
        Instant reference = step.getCompletedAt() != null ? step.getCompletedAt() : step.getStartedAt();
        return reference != null && reference.isAfter(resumeThreshold);
    }

    private static class RunProgress {
        private boolean workflowQueued;
        private final Set<Long> jobStarted = ConcurrentHashMap.newKeySet();
        private final Set<Long> jobFinished = ConcurrentHashMap.newKeySet();
        private final Set<String> stepStarted = ConcurrentHashMap.newKeySet();
        private final Set<String> stepCompleted = ConcurrentHashMap.newKeySet();
    }
}
