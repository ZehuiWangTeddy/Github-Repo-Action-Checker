package org.nhlstenden.moniter.scheduler;

import org.nhlstenden.moniter.api.GithubClient;
import org.nhlstenden.moniter.model.Job;
import org.nhlstenden.moniter.model.Step;
import org.nhlstenden.moniter.model.WorkflowRun;
import org.nhlstenden.moniter.storage.StateStore;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PollingService {
    private final ScheduledExecutorService scheduler;
    private final GithubClient githubClient;
    private final StateStore stateStore;
    private final String owner;
    private final String repo;
    private boolean isFirstPoll;
    private final ZonedDateTime startupTime;


    public PollingService(GithubClient githubClient, StateStore stateStore, String owner, String repo, boolean isFirstStart, ZonedDateTime startupTime) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.githubClient = githubClient;
        this.stateStore = stateStore;
        this.owner = owner;
        this.repo = repo;
        this.isFirstPoll = isFirstStart;
        this.startupTime = startupTime;
    }

    public void start(long intervalSeconds) {
        scheduler.scheduleAtFixedRate(
                this::pollOnce,
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    private void pollOnce() {
        boolean hasNewEvent = false;
        try {

            System.out.println("Polling GitHub workflows...");
            //The last poll time
            ZonedDateTime checkpoint = stateStore.getLastCheckpoint().orElse(startupTime);
            List<WorkflowRun> runs = githubClient.listWorkflowRuns(owner, repo);

            //Check repo has run actions before or not
            if (runs.isEmpty()) {
                System.out.println("No workflow runs found");
                isFirstPoll = false;
                return;
            }

            boolean foundAfterCheckpoint = false;
            ZonedDateTime maxActionTime = checkpoint;

            for (WorkflowRun run : runs) {

                //Only work with the run update after checkpoint
                ZonedDateTime updatedAt = run.getUpdatedAt();
                if (updatedAt == null || !updatedAt.isAfter(checkpoint)) {
                    continue;
                }

                foundAfterCheckpoint = true;

                //Note the latest time actually processed of this poll
                if (updatedAt.isAfter(maxActionTime)) {
                    maxActionTime = updatedAt;
                }

                //Workflow run started / updated
                WorkflowRun storedRun = stateStore.getWorkflowRunById(run.getId());
                if (storedRun == null) {
                    stateStore.saveWorkflowRun(run);
                    hasNewEvent = true;
                    System.out.println(run);
                } else if (storedRun.hasStatusOrConclusionChanged(run)) {
                    stateStore.updateWorkflowRun(run);
                    hasNewEvent = true;
                    System.out.println(run);
                }

                //Process Jobs and steps
                List<Job> jobs = githubClient.listJobs(owner, repo, run.getId());
                for (Job job : jobs) {
                    //Job started
                    if (!stateStore.hasJob(job.getId())) {
                        System.out.println(formatJobStarted(run, job));
                        stateStore.saveJob(job, run.getId());
                    }
                    //Job completed
                    if (job.getConclusion() != null && !stateStore.hasJobComplete(job.getId())) {
                        System.out.println(formatJobCompleted(run, job));
                        stateStore.markJobComplete(job.getId());
                    }

                    for (Step step : job.getSteps()) {
                        //Step started
                        if (!stateStore.hasStep(job.getId(), step.getName())) {
                            System.out.println(formatStepStarted(run, job, step));
                            stateStore.saveStep(step, job.getId());
                        }

                        //Step completed
                        if (step.getConclusion() != null &&
                                !stateStore.hasStepComplete(job.getId(), step.getName())) {
                            System.out.println(formatStepCompleted(run, job, step));
                            stateStore.markStepComplete(job.getId(), step.getName());
                        }
                    }
                }
            }

            //The repo has workflow before but the time is before checkpoint
            if (!foundAfterCheckpoint) {
                if (isFirstPoll) {
                    System.out.println("Waiting for new action start...");
                } else {
                    System.out.println("No workflow runs found since last polling");
                }
                isFirstPoll = false;
                return;
            }

            //Only update checkpoint after process runs
            if (hasNewEvent) {
                stateStore.updateLastCheckpoint(maxActionTime);
            }

            if (!hasNewEvent && !isFirstPoll) {
                System.out.println("No new workflow runs found since last polling");
            }
            isFirstPoll = false;
        } catch (Exception e) {
            System.err.println("Polling failed: " + e.getMessage());
        }
    }

    private String formatJobStarted(WorkflowRun run, Job job) {
        return String.format(
                "Time=%s Event=JOB_STARTED Run=%d Job=%d Name=\"%s\" Branch=%s Sha=%s",
                job.getStartedAt(),
                run.getId(),
                job.getId(),
                job.getName(),
                run.getHeadBranch(),
                run.getHeadSha()
        );
    }

    private String formatJobCompleted(WorkflowRun run, Job job) {
        return String.format(
                "Time=%s Event=JOB_COMPLETED Run=%d Job=%d Name=\"%s\" Branch=%s Sha=%s conclusion=%s",
                job.getCompletedAt(),
                run.getId(),
                job.getId(),
                job.getName(),
                run.getHeadBranch(),
                run.getHeadSha(),
                job.getConclusion()
        );
    }

    private String formatStepStarted(WorkflowRun run, Job job, Step step) {
        return String.format(
                "Time=%s Event=STEP_STARTED Run=%d Job=%d Job_name=\"%s\" Step=\"%s\" Branch=%s Sha=%s",
                step.getStartedAt(),
                run.getId(),
                job.getId(),
                job.getName(),
                step.getName(),
                run.getHeadBranch(),
                run.getHeadSha()
        );
    }

    private String formatStepCompleted(WorkflowRun run, Job job,  Step step) {
        return String.format(
                "Time=%s Event=Step_COMPLETED Run=%d Job=%d Name=\"%s\" Step=\"%s\" Branch=%s Sha=%s Conclusion=%s",
                step.getCompletedAt(),
                run.getId(),
                job.getId(),
                job.getName(),
                step.getName(),
                run.getHeadBranch(),
                run.getHeadSha(),
                step.getConclusion()
        );
    }

    public void stop() {
        scheduler.shutdown();
        System.out.println("PollingService stopped");
    }
}
