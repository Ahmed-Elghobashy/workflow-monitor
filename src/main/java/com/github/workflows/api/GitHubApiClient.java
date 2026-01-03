package com.github.workflows.api;

import com.github.workflows.model.Job;
import com.github.workflows.model.Step;
import com.github.workflows.model.WorkflowRun;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handles all GitHub API interactions for workflow runs, jobs, and steps.
 * This layer abstracts the GitHub API client from the monitoring logic.
 */
public class GitHubApiClient {
    private final GitHub github;
    private final GHRepository repository;

    public GitHubApiClient(String token, String repositoryName) throws IOException {
        this.github = GitHub.connectUsingOAuth(token);
        this.repository = github.getRepository(repositoryName);
    }

    /**
     * Fetch workflow runs since the given timestamp.
     * @param since Only return runs created after this time
     * @return List of workflow run information
     */
    public List<WorkflowRun> getWorkflowRunsSince(Instant since) throws IOException {
        List<WorkflowRun> workflowRuns = new ArrayList<>();
        
        try {
            // Query workflow runs from the repository
            PagedIterable<GHWorkflowRun> runs = repository.queryWorkflowRuns().list();
            
            for (GHWorkflowRun run : runs) {
                try {
                    Date createdAtDate = run.getCreatedAt();
                    if (createdAtDate == null) {
                        continue;
                    }
                    
                    Instant runCreatedAt = createdAtDate.toInstant();
                    Date updatedAtDate = run.getUpdatedAt();
                    Instant runUpdatedAt = updatedAtDate != null ? updatedAtDate.toInstant() : runCreatedAt;
                    
                    // Filter by timestamp - only include runs updated after 'since'
                    if (since != null && !runUpdatedAt.isAfter(since)) {
                        continue;
                    }
                    
                    // Extract workflow run information
                    long id = run.getId();
                    String name = run.getName();
                    String branch = run.getHeadBranch() != null ? run.getHeadBranch() : "";
                    String commitSha = run.getHeadSha() != null ? run.getHeadSha() : "";
                    String status = run.getStatus() != null ? run.getStatus().toString() : null;
                    String conclusion = run.getConclusion() != null ? run.getConclusion().toString() : null;
                    
                    workflowRuns.add(
                        new WorkflowRun(
                            id,
                            name,
                            branch,
                            commitSha,
                            runCreatedAt,
                            runUpdatedAt,
                            status,
                            conclusion
                        )
                    );
                } catch (Exception e) {
                    // Skip runs that fail to process
                    System.err.println("Warning: Failed to process workflow run: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching workflow runs: " + e.getMessage());
            e.printStackTrace();
        }
        
        return workflowRuns;
    }

    /**
     * Fetch jobs for a specific workflow run.
     * @param runId The workflow run ID
     * @return List of job information
     */
    public List<Job> getJobsForRun(long runId) throws IOException {
        List<Job> jobs = new ArrayList<>();
        
        try {
            // Get the workflow run
            GHWorkflowRun run = repository.getWorkflowRun(runId);
            if (run == null) {
                return jobs;
            }
            
            // Get jobs for this workflow run
            PagedIterable<GHWorkflowJob> workflowJobs = run.listJobs();
            
            for (GHWorkflowJob job : workflowJobs) {
                try {
                    long id = job.getId();
                    String name = job.getName();
                    String status = job.getStatus() != null ? job.getStatus().toString() : null;
                    String conclusion = job.getConclusion() != null ? job.getConclusion().toString() : null;
                    
                    // Convert Date to Instant
                    Instant startedAt = job.getStartedAt() != null 
                        ? job.getStartedAt().toInstant() 
                        : null;
                    Instant completedAt = job.getCompletedAt() != null 
                        ? job.getCompletedAt().toInstant() 
                        : null;
                    
                    jobs.add(new Job(id, name, status, conclusion, startedAt, completedAt));
                } catch (Exception e) {
                    System.err.println("Warning: Failed to process job: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to fetch jobs for run " + runId + ": " + e.getMessage());
        }
        
        return jobs;
    }

    /**
     * Fetch steps for a specific job.
     * @param jobId The job ID
     * @return List of step information
     */
    public List<Step> getStepsForJob(long jobId) throws IOException {
        List<Step> steps = new ArrayList<>();
        
        try {
            // Get the job
            GHWorkflowJob job = repository.getWorkflowJob(jobId);
            if (job == null) {
                return steps;
            }
            
            // Get steps for this job
            // The kohsuke API returns steps as a list, but the exact type may vary
            try {
                Object stepsObj = job.getSteps();
                if (stepsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> workflowSteps = (List<Object>) stepsObj;
                    
                    for (Object stepObj : workflowSteps) {
                        try {
                            // Use reflection to access step properties
                            String name = getMethodValue(stepObj, "getName", String.class);
                            Object statusObj = getMethodValue(stepObj, "getStatus", Object.class);
                            Object conclusionObj = getMethodValue(stepObj, "getConclusion", Object.class);
                            Date startedAtDate = getMethodValue(stepObj, "getStartedAt", Date.class);
                            Date completedAtDate = getMethodValue(stepObj, "getCompletedAt", Date.class);
                            
                            String status = statusObj != null ? statusObj.toString() : null;
                            String conclusion = conclusionObj != null ? conclusionObj.toString() : null;
                            
                            Instant startedAt = startedAtDate != null ? startedAtDate.toInstant() : null;
                            Instant completedAt = completedAtDate != null ? completedAtDate.toInstant() : null;
                            
                            steps.add(new Step(name, status, conclusion, startedAt, completedAt));
                        } catch (Exception e) {
                            System.err.println("Warning: Failed to process step: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to get steps from job: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to fetch steps for job " + jobId + ": " + e.getMessage());
        }
        
        return steps;
    }

    /**
     * Helper method to safely invoke a method via reflection.
     */
    @SuppressWarnings("unchecked")
    private <T> T getMethodValue(Object obj, String methodName, Class<T> returnType) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            return returnType.cast(result);
        } catch (Exception e) {
            return null;
        }
    }

}
