package com.lld.todolist.filters;

import com.lld.todolist.interfaces.TaskFilterStrategy;
import com.lld.todolist.models.Task;

public class TagFilter implements TaskFilterStrategy {

    private final String tag;

    public TagFilter(String tag) {
        this.tag = tag;
    }

    @Override
    public boolean apply(Task task) {
        return task.getTags().contains(tag);
    }
}
