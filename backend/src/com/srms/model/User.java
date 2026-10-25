package com.srms.model;

/**
 * A login account. Passwords are never stored as plain text, only as a
 * salted hash. A student account is linked to one student record by roll number.
 */
public class User {

    private final String username;
    private final String role;      // "teacher" or "student"
    private final String rollNo;    // the student this account belongs to; empty for teachers
    private boolean mustChangePassword;
    private String salt;
    private String passwordHash;

    public User(String username, String role, String rollNo,
                boolean mustChangePassword, String salt, String passwordHash) {
        this.username = username;
        this.role = role;
        this.rollNo = rollNo;
        this.mustChangePassword = mustChangePassword;
        this.salt = salt;
        this.passwordHash = passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getRollNo() {
        return rollNo;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public String getSalt() {
        return salt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** Replaces the stored credentials (used for password changes and resets). */
    public void setCredentials(String salt, String passwordHash, boolean mustChangePassword) {
        this.salt = salt;
        this.passwordHash = passwordHash;
        this.mustChangePassword = mustChangePassword;
    }
}
