package com.lld.todolist.filters;

import com.lld.todolist.interfaces.TaskFilterStrategy;
import com.lld.todolist.models.Task;
import com.lld.todolist.models.TaskStatus;

public class StatusFilter implements TaskFilterStrategy {

    private final TaskStatus status;

    public StatusFilter(TaskStatus status) {
        this.status = status;
    }

    @Override
    public boolean apply(Task task) {
        return task.getStatus() == status;
    }
}
