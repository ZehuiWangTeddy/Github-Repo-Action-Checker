package org.nhlstenden.moniter.cli;

import org.apache.commons.cli.CommandLine;
import org.nhlstenden.moniter.api.GithubClient;
import org.nhlstenden.moniter.scheduler.PollingService;
import org.nhlstenden.moniter.storage.StateStore;

import java.time.ZonedDateTime;
import java.util.Optional;


public class GithubMonitorCli {
    public static void main(String[] args) throws Exception {

        //Parse command-line argument
        CommandLine cmd = CliOptions.parse(args);

        //Validate repo format
        String repo = cmd.getOptionValue("repo"); // owner/name
        if (!repo.contains("/") || repo.split("/").length != 2) {
            System.err.println("Repository must be in owner/repo format " + repo);
            System.exit(1);
        }

        //Read token and polling interval
        String token = cmd.getOptionValue("token");
        long interval = Long.parseLong(
                cmd.getOptionValue("interval", "10")
        );

        //Split owner and repo name
        String[] parts = repo.split("/");
        String owner = parts[0];
        String repoName = parts[1];

        //Initialize client
        GithubClient githubClient = new GithubClient(token);

        //Initialize state store
        StateStore stateStore = new StateStore("state.db");
        stateStore.init();

        //Determine whether this is the first start up
        ZonedDateTime startupTime = ZonedDateTime.now();
        Optional<ZonedDateTime> lastCheckpoint = stateStore.getLastCheckpoint();
        boolean isFirstStart = lastCheckpoint.isEmpty();

        if (isFirstStart) {
            // ignore workflow history
            ZonedDateTime now = ZonedDateTime.now();
            stateStore.updateLastCheckpoint(now);
            System.out.println("Stater checking from: " + startupTime + ". Previous workflow runs are ignored.");
        } else {
            //Resume from last saved checkpoint
            System.out.println("Last checkpoint: " + lastCheckpoint.get());
        }

        //Start polling service
        PollingService pollingService = new PollingService(githubClient, stateStore, owner, repoName, isFirstStart, startupTime);
        pollingService.start(interval);

        System.out.println("Monitoring GitHub Actions for " + repo);
        System.out.println("Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down GitHub Actions for " + repo);
            pollingService.stop();
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
        }
    }
}
