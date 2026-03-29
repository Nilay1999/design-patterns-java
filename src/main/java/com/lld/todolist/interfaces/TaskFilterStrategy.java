package com.lld.todolist.interfaces;

import com.lld.todolist.models.Task;

public interface TaskFilterStrategy {
    boolean apply(Task task);
}