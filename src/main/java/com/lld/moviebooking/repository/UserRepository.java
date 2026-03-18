package com.lld.moviebooking.repository;

import com.lld.moviebooking.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserRepository {
    private final Map<String, User> users = new HashMap<>();

    public void add(User user) {
        users.put(user.getId(), user);
    }

    public User get(String id) {
        return users.get(id);
    }

    public List<User> getAll() {
        return new ArrayList<>(users.values());
    }
}
