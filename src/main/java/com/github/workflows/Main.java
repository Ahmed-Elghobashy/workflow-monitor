package com.github.workflows;

import com.github.workflows.monitor.WorkflowMonitor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "workflow-monitor",
    description = "Monitor GitHub workflow runs and report events to stdout",
    mixinStandardHelpOptions = true
)
public class Main implements Runnable {
    
    @Parameters(
        index = "0",
        description = "Repository in format 'owner/repo' (e.g., 'octocat/Hello-World')"
    )
    private String repository;
    
    @Option(
        names = {"--token", "-t"},
        description = "GitHub personal access token",
        required = true
    )
    private String token;
    
    @Override
    public void run() {
        WorkflowMonitor monitor = new WorkflowMonitor(repository, token);
        
        // Handle graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("\nShutting down gracefully...");
            monitor.stop(); // This saves state and shuts down the executor
        }));

        try {
            monitor.start();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
