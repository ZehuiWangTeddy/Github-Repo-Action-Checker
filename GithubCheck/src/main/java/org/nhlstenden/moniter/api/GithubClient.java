package org.nhlstenden.moniter.api;

import org.nhlstenden.moniter.model.Job;
import org.nhlstenden.moniter.model.Step;
import org.nhlstenden.moniter.model.WorkflowRun;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class GithubClient {
    private static final String BASE_URL = "https://api.github.com";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String token;

    public GithubClient(String token) {
        this.token = token;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     *
     * Fetch all workflow runs for repo
     * Convert Json response in to workflow model
     * @param owner GitHub owner
     * @param repo  GitHub repo name
     */
    public List<WorkflowRun> listWorkflowRuns(String owner, String repo) throws IOException, InterruptedException {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/actions/runs";

        System.out.println("Request URL: " + url);
        System.out.println("Using Authorization: token <hidden>");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "token " + token)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = this.objectMapper.readTree(response.body());
        JsonNode runsNode = root.get("workflow_runs");

        if (runsNode == null || !runsNode.isArray()) {
            return Collections.emptyList();
        }

        List<WorkflowRun> workflowRuns = new ArrayList<>();
        for (JsonNode runNode : runsNode) {
            workflowRuns.add(parseWorkflowRun(runNode));
        }
        return workflowRuns;
    }

    /**
     * Parse a single workflow run JSON node into workflow model
     */
    private WorkflowRun parseWorkflowRun(JsonNode node) {
        WorkflowRun run = new WorkflowRun();

        run.setId(node.get("id").asLong());
        run.setName(node.get("name").asText());
        run.setStatus(node.get("status").asText());

        run.setConclusion(
                node.hasNonNull("conclusion")
                        ? node.get("conclusion").asText()
                        : null
        );

        run.setHeadBranch(node.get("head_branch").asText());
        run.setHeadSha(node.get("head_sha").asText());

        run.setCreatedAt(
                node.hasNonNull("created_at")
                        ? ZonedDateTime.parse(node.get("created_at").asText())
                        : null
        );

        run.setUpdatedAt(
                node.hasNonNull("updated_at")
                        ? ZonedDateTime.parse(node.get("updated_at").asText())
                        : null
        );

        return run;
    }

    /**
     * Fetch all jobs and steps for a workflow
     */
    public List<Job> listJobs(String owner, String repo, long runId) throws IOException, InterruptedException {
        String url = BASE_URL + "/repos/" + owner + "/" + repo +
                "/actions/runs/" + runId + "/jobs";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "token " + token)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode jobsNode = root.get("jobs");
        List<Job> jobs = new ArrayList<>();

        for (JsonNode jobNode : jobsNode) {
            Job job = objectMapper.treeToValue(jobNode, Job.class);

            //Manually parse steps for finer control
            JsonNode stepsNode = jobNode.get("steps");
            if (stepsNode != null && stepsNode.isArray()) {
                List<Step> steps = new ArrayList<>();
                for (JsonNode stepNode : stepsNode) {
                    Step step = objectMapper.treeToValue(stepNode, Step.class);
                    steps.add(step);
                }
                job.setSteps(steps);
            }
            jobs.add(job);
        }

        return jobs;
    }

}
