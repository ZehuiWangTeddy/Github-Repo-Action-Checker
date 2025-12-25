package org.nhlstenden.moniter.storage;

import org.nhlstenden.moniter.model.Job;
import org.nhlstenden.moniter.model.Step;
import org.nhlstenden.moniter.model.WorkflowRun;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.Optional;

public class StateStore {
    private final Connection connection;

    public StateStore(String dbFile) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
    }

    public void init() throws SQLException {
        String workflowTable = """
                CREATE TABLE IF NOT EXISTS workflow_runs (
                    id INTEGER PRIMARY KEY,
                    status TEXT,
                    conclusion TEXT,
                    headBranch TEXT,
                    headSha TEXT,
                    completed_at TEXT
                )
                """;

        String metaTable = """
                CREATE TABLE IF NOT EXISTS meta (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
                """;
        String JobTable = """
                CREATE TABLE IF NOT EXISTS jobs (
                    id INTEGER PRIMARY KEY,
                    run_id INTEGER,
                    name TEXT,
                    status TEXT,
                    conclusion TEXT,
                    completed_at TEXT,
                    completed INTEGER DEFAULT 0
                )
                """;
        String StepTable = """
                CREATE TABLE IF NOT EXISTS steps (
                    job_id INTEGER,
                    name TEXT,
                    status TEXT,
                    conclusion TEXT,
                    completed_at TEXT,
                    completed INTEGER DEFAULT 0,
                    UNIQUE(job_id, name)
                )
                """;

        try (Statement statement = this.connection.createStatement()) {
            statement.execute(workflowTable);
            statement.execute(metaTable);
            statement.execute(JobTable);
            statement.execute(StepTable);
        }
    }

    public void saveWorkflowRun(WorkflowRun workflowRun) throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO workflow_runs
                (id, status, conclusion, headBranch, headSha, completed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, workflowRun.getId());
            sta.setString(2, workflowRun.getStatus());
            sta.setString(3, workflowRun.getConclusion());
            sta.setString(4, workflowRun.getHeadBranch());
            sta.setString(5, workflowRun.getHeadSha());
            sta.setString(6, workflowRun.getUpdatedAt() != null ? workflowRun.getUpdatedAt().toString() : null);
            sta.executeUpdate();
        }
    }

    public boolean hasWorkflowRun(long workflowRunId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM workflow_runs WHERE id=?";
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, workflowRunId);
            ResultSet rs = sta.executeQuery();
            return rs.next() && rs.getLong(1) > 0;
        }
    }

    public Optional<ZonedDateTime> getLastCheckpoint() throws SQLException {
        String sql = "SELECT value FROM meta WHERE key = 'last_checkpoint'";
        try (Statement statement = this.connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return Optional.of(ZonedDateTime.parse(rs.getString("value")));
            }
            return Optional.empty();
        }
    }

    public void updateLastCheckpoint(ZonedDateTime lastCheckpoint) throws SQLException {
        String sql = """
                INSERT INTO meta (key, value)
                VALUES ('last_checkpoint', ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """;

        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setString(1, lastCheckpoint.toString());
            sta.executeUpdate();
        }
    }

    public void updateWorkflowRun(WorkflowRun workflowRun) throws SQLException {
        String sql = "UPDATE workflow_runs SET status=?, conclusion=?, completed_at=? WHERE id=?";
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setString(1, workflowRun.getStatus());
            sta.setString(2, workflowRun.getConclusion());
            sta.setString(3, workflowRun.getUpdatedAt().toString());
            sta.setLong(4, workflowRun.getId());
            sta.executeUpdate();
        }
    }

    public WorkflowRun getWorkflowRunById(long id) throws SQLException {
        String sql = "SELECT id, status, conclusion, completed_at FROM workflow_runs WHERE id=?";
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, id);
            ResultSet rs = sta.executeQuery();
            if (rs.next()) {
                WorkflowRun workflowRun = new WorkflowRun();
                workflowRun.setId(rs.getLong("id"));
                workflowRun.setStatus(rs.getString("status"));
                workflowRun.setConclusion(rs.getString("conclusion"));
                String updatedAtString = rs.getString("completed_at");
                if (updatedAtString != null) {
                    workflowRun.setUpdatedAt(ZonedDateTime.parse(updatedAtString));
                }
                return workflowRun;
            }
            return null;
        }
    }

    public boolean hasJob(long jobId) throws SQLException {
        String sql = "SELECT 1 FROM jobs WHERE id = ?";
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, jobId);
            return  sta.executeQuery().next();
        }
    }

    public void saveJob(Job job, long runId) throws SQLException {
        String sql = """
        INSERT OR IGNORE INTO jobs
        (id, run_id, name, status, conclusion, completed_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, job.getId());
            sta.setLong(2, runId);
            sta.setString(3, job.getName());
            sta.setString(4, job.getStatus());
            sta.setString(5, job.getConclusion());
            sta.setString(6, job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
            sta.executeUpdate();
        }
    }

    public boolean hasJobComplete(long jobId) throws SQLException {
        String sql = "SELECT completed FROM jobs WHERE id = ?";
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, jobId);
            ResultSet rs = sta.executeQuery();
            return rs.next() && rs.getInt("completed") == 1;
        }
    }


    public void markJobComplete(long jobId) throws SQLException {
        String sql = "UPDATE jobs SET completed = 1 WHERE id = ?";
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, jobId);
            sta.executeUpdate();
        }
    }

    public boolean hasStep(long jobId, String stepName) throws SQLException {
        String sql = "SELECT 1 FROM steps WHERE job_id = ? AND name = ?";
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, jobId);
            sta.setString(2, stepName);
            return  sta.executeQuery().next();
        }
    }

    public void saveStep(Step step, long jobId) throws SQLException {
        String sql = """
        INSERT OR IGNORE INTO steps
        (job_id, name, status, conclusion, completed_at)
        VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, jobId);
            sta.setString(2, step.getName());
            sta.setString(3, step.getStatus());
            sta.setString(4, step.getConclusion());
            sta.setString(5, step.getCompletedAt() != null ? step.getCompletedAt().toString() : null);
            sta.executeUpdate();
        }
    }

    public boolean hasStepComplete(long jobId, String stepName) throws SQLException {
        String sql = "SELECT completed FROM steps WHERE job_id = ? AND name = ?";
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, jobId);
            sta.setString(2, stepName);
            ResultSet rs = sta.executeQuery();
            return rs.next() && rs.getInt("completed") == 1;
        }
    }

    public void markStepComplete(long jobId, String stepName) throws SQLException {
        String sql = "UPDATE steps SET completed = 1 WHERE job_id = ? AND name = ?";
        try (PreparedStatement sta = this.connection.prepareStatement(sql)) {
            sta.setLong(1, jobId);
            sta.setString(2, stepName);
            sta.executeUpdate();
        }
    }
}
