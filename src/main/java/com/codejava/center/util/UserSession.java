package com.codejava.center.util;

import com.codejava.center.domain.User;

public class UserSession {
    private static User currentUser;

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void cleanUserSession() {
        currentUser = null;
    }
}