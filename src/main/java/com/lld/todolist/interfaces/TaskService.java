package com.lld.todolist.interfaces;

import java.util.List;

import com.lld.todolist.dto.TaskCreateRequest;
import com.lld.todolist.models.Task;
import com.lld.todolist.models.TaskList;
import com.lld.todolist.models.TaskStatus;

public interface TaskService {
    TaskList createTaskList(String userId, String listName);

    Task addTask(String listId, TaskCreateRequest request);

    void moveTask(String taskId, String targetListId);

    void updateTaskStatus(String taskId, TaskStatus newStatus);

    List<Task> filterTasks(List<Task> tasks, TaskFilterStrategy strategy);
}