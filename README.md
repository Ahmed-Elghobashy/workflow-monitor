# Workflow Monitor CLI

A Picocli-based command line tool that continuously watches GitHub Actions workflow runs for a repository and prints every workflow, job, and step event as readable single-line entries. It tracks its own state so successive runs only emit updates that happened since the last session.

## Requirements

- Java 17+
- GitHub personal access token with `repo` scope (or `public_repo` for public-only monitoring)
- Local filesystem permissions to read/write `~/.workflows-status/<owner>/<repo>/state.json`

## Installation

Build the CLI scripts with Gradle:

```bash
./gradlew installDist
```

This produces an executable command at `build/install/workflow-monitor/bin/workflow-monitor`.

Optional: build a single runnable JAR:

```bash
./gradlew shadowJar
# JAR lives at build/libs/workflows-status-1.0.0-all.jar
```

## Usage

```bash
build/install/workflow-monitor/bin/workflow-monitor <owner>/<repo> --token <github-token> [options]
```

Key options:

| Option | Description |
| --- | --- |
| `--token`, `-t` | GitHub personal access token *(required)* |
| `--interval`, `-i` | Poll interval in seconds (default: `2`) |

Examples:

```bash
# Using the installed CLI script
build/install/workflow-monitor/bin/workflow-monitor octocat/Hello-World --token ghp_xxx

# Using the runnable JAR instead
java -jar build/libs/workflows-status-1.0.0-all.jar octocat/Hello-World --token ghp_xxx
```

The monitor can be stopped with `Ctrl+C`. A shutdown hook flushes the in-memory state before exiting.

## Output Format

Events stream to stdout as colorized lines:

```
[2026-01-03 15:48:43] WORKFLOW QUEUED  Run #206... "Manual Artifact Workflow" (branch: main, commit: 36d4953...)
[2026-01-03 15:48:47] JOB STARTED      Run #206... Job #593... "Build & Test (Python 3.11 • ubuntu-latest)"
[2026-01-03 15:48:47] STEP SUCCESS     Run #206... Job #593... step "Run tests" (1s)
[2026-01-03 15:48:51] JOB FINISHED     Run #206... Job #593... "Build & Test..." => SUCCESS (13s)
```

Each line includes:

- ISO-like local timestamp
- Event label (`WORKFLOW QUEUED`, `JOB STARTED`, `JOB FINISHED`, `STEP STARTED`, `STEP SUCCESS`, `STEP FAILURE`, …)
- Run ID, job ID, and step name (with consistent coloring)
- Branch + commit for workflows, durations and conclusions for jobs/steps

## State & Resume Behavior

On first run for a repository, every visible workflow/job/step event is reported. After the monitor exits, it saves the latest processed timestamp to:

```
~/.workflows-status/<owner>/<repo>/state.json
```

Subsequent runs fetch workflow runs updated after that timestamp and filter out jobs/steps whose completion timestamps are older. This means only newly completed jobs/steps (and newer workflow queues) appear after a restart.

> **Permissions tip:** if your environment has strict home-directory permissions, ensure the user running the CLI can create the `~/.workflows-status` directory tree and read/write the JSON state file.

## Sample Test Repository

A ready-to-push sample exists under `sample-test-repo/`. To try the monitor without risking production repos:

1. Create an empty GitHub repo (e.g., `workflow-monitor-playground`).
2. Copy the contents of `sample-test-repo` into it, commit, and push to `main`.
3. Push a new commit to trigger the matrix CI workflow, or trigger the manual workflow from the Actions tab.
4. Run `workflow-monitor <you>/workflow-monitor-playground --token ...` to watch events roll in.

The sample workflows include:

- Matrix CI (`ci.yml`) with multiple OS/Python combinations plus an intentional failure job.
- Manual artifact workflow (`manual-artifact.yml`) that uploads/downloads artifacts and sleeps to showcase longer steps.

## Developing Locally

- `./gradlew run --args "<owner>/<repo> --token xxx"` – run without installing.
- `./gradlew test` – execute tests.
- `./gradlew installDist` – rebuild CLI scripts after code changes.

Feel free to tweak polling intervals, logging, or event formatting; the `ConsoleSpinner` and `EventReporter` are easy to adjust for different output styles.
