package com.lld.todolist.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Task {

    private final String taskId;
    private final String title;
    private final String description;
    private final TaskStatus status;
    private final Instant dueDate;
    private final List<String> tags;

    private Task(Builder builder) {
        this.taskId = UUID.randomUUID().toString();
        this.title = builder.title;
        this.description = builder.description;
        this.status = builder.status != null ? builder.status : TaskStatus.TODO;
        this.dueDate = builder.dueDate;
        this.tags = builder.tags != null ? List.copyOf(builder.tags) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public List<String> getTags() {
        return tags;
    }

    public static class Builder {
        private String title;
        private String description;
        private Instant dueDate;
        private TaskStatus status;
        private List<String> tags;

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setDueDate(Instant dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public Builder setStatus(TaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder setTags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Task build() {
            if (title == null || title.isBlank()) {
                throw new IllegalStateException("Title is required");
            }
            return new Task(this);
        }
    }
}