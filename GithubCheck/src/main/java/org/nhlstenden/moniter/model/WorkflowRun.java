package org.nhlstenden.moniter.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;
import java.util.List;

public class WorkflowRun {
    private long id;
    private String name;
    private String status;
    private String conclusion;
    private String headBranch;
    private String headSha;
    @JsonProperty("created_at")
    private ZonedDateTime createdAt;
    @JsonProperty("updated_at")
    private ZonedDateTime updatedAt;
    private List<Job> jobs;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getHeadBranch() {
        return headBranch;
    }

    public void setHeadBranch(String headBranch) {
        this.headBranch = headBranch;
    }

    public String getHeadSha() {
        return headSha;
    }

    public void setHeadSha(String headSha) {
        this.headSha = headSha;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public void setJobs(List<Job> jobs) {
        this.jobs = jobs;
    }

    public boolean hasStatusOrConclusionChanged(WorkflowRun other) {
        if (other == null) return false;

        boolean statusChanged = (this.status == null && other.status != null)
                || (this.status != null && !this.status.equals(other.status));

        boolean conclusionChanged = (this.conclusion == null && other.conclusion != null)
                || (this.conclusion != null && !this.conclusion.equals(other.conclusion));

        boolean completedAtChanged = (this.updatedAt == null && other.updatedAt != null)
                || (this.updatedAt != null && !this.updatedAt.equals(other.updatedAt));

        return statusChanged || conclusionChanged || completedAtChanged;
    }

    @Override
    public String toString() {
        return String.format(
                "Workflow: %s | Status: %s | Conclusion: %s | Branch: %s | SHA: %s | CreatedAt: %s | UpdatedAt: %s",
                name, status, conclusion == null ? "N/A" : conclusion,
                headBranch, headSha,
                createdAt != null ? createdAt : "N/A",
                updatedAt != null ? updatedAt : "N/A"
        );
    }
}
