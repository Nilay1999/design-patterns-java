package com.lld.todolist.services;

import java.util.List;
import java.util.stream.Collectors;

import com.lld.todolist.dto.TaskCreateRequest;
import com.lld.todolist.dto.TaskFilter;
import com.lld.todolist.dto.TaskSort;
import com.lld.todolist.interfaces.TaskFilterStrategy;
import com.lld.todolist.interfaces.TaskService;
import com.lld.todolist.models.Task;
import com.lld.todolist.models.TaskList;
import com.lld.todolist.models.TaskStatus;

public class TaskServiceImpl implements TaskService {

    @Override
    public TaskList createTaskList(String userId, String listName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createTaskList'");
    }

    @Override
    public Task addTask(String listId, TaskCreateRequest request) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addTask'");
    }

    @Override
    public List<Task> filterTasks(List<Task> tasks, TaskFilterStrategy strategy) {
        return tasks.stream()
                .filter(strategy::apply)
                .collect(Collectors.toList());
    }

    @Override
    public void moveTask(String taskId, String targetListId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'moveTask'");
    }

    @Override
    public void updateTaskStatus(String taskId, TaskStatus newStatus) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateTaskStatus'");
    }
}
