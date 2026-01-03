package com.github.workflows.io;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Manages persistent state to track the last run time for a repository.
 * State is stored in a local file: ~/.workflows-status/<owner>/<repo>/state.json
 * TODO : DO I need to get permissions to write to the file?
 */
public class StateManager {
    private final String repository;
    private final File stateFile;
    private final Gson gson = new Gson();

    public StateManager(String repository) {
        this.repository = repository;
        
        // Initialize state file location: ~/.workflows-status/<owner>/<repo>/state.json
        Path stateDir = Paths.get(
            System.getProperty("user.home"),
            ".workflows-status",
            repository.replace("/", File.separator)
        );
        
        // Create directory if it doesn't exist
        try {
            Files.createDirectories(stateDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create state directory: " + stateDir, e);
        }
        
        this.stateFile = stateDir.resolve("state.json").toFile();
    }

    /**
     * Load the last run time from persistent storage.
     * Returns null if no previous state exists.
     */
    public Instant loadLastRunTime() {
        if (!stateFile.exists()) {
            return null;
        }
        
        try {
            String content = Files.readString(stateFile.toPath());
            State state = gson.fromJson(content, State.class);
            return state.lastRunTime != null ? Instant.parse(state.lastRunTime) : null;
        } catch (IOException e) {
            System.err.println("Warning: Failed to load state: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse state file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Save the current run time to persistent storage.
     */
    public void saveLastRunTime(Instant time) {
        try {
            State state = new State(time.toString());
            String json = gson.toJson(state);
            Files.writeString(stateFile.toPath(), json);
        } catch (IOException e) {
            // Log error but don't fail - state saving is not critical
            System.err.println("Warning: Failed to save state: " + e.getMessage());
        }
    }

    /**
     * Internal class for JSON serialization.
     */
    private static class State {
        @SerializedName("lastRunTime")
        private final String lastRunTime;

        public State(String lastRunTime) {
            this.lastRunTime = lastRunTime;
        }
    }
}

