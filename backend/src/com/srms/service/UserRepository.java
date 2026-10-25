package com.srms.service;

import com.srms.model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores login accounts in a CSV file (users.csv), the same way
 * StudentRepository stores students. Passwords are hashed with PBKDF2
 * and a random salt per user.
 *
 * CSV format per line: username,role,rollNo,mustChange,salt,hash
 *
 * Every student gets an account whose username and first password are
 * their roll number; they are forced to change it at first login.
 */
public class UserRepository {

    private static final String HEADER = "username,role,rollNo,mustChange,salt,hash";
    /** Kept modest so importing hundreds of students stays fast. A production system would use far more. */
    private static final int ITERATIONS = 10_000;

    private final Path path;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public UserRepository(String filePath) throws IOException {
        this.path = Paths.get(filePath);
        load();
        if (find("teacher") == null) {
            add(newUser("teacher", "teacher", "", "teacher123", false));
            save();
        }
    }

    /** Returns the user if the username and password match, otherwise null. */
    public User authenticate(String username, String password) {
        User user = find(username);
        if (user == null) {
            return null;
        }
        byte[] salt = Base64.getDecoder().decode(user.getSalt());
        byte[] expected = user.getPasswordHash().getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(password, salt).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual) ? user : null;
    }

    public User find(String username) {
        return users.get(key(username));
    }

    /** Creates a student account (password = roll number) if there isn't one. Returns true if created. */
    public boolean createStudentAccount(String rollNo) throws IOException {
        if (find(rollNo) != null) {
            return false;
        }
        add(newUser(rollNo, "student", rollNo, rollNo, true));
        save();
        return true;
    }

    /** Creates any missing student accounts, saving the file once at the end. */
    public void ensureStudentAccounts(Collection<String> rollNos) throws IOException {
        boolean changed = false;
        for (String rollNo : rollNos) {
            if (find(rollNo) == null) {
                add(newUser(rollNo, "student", rollNo, rollNo, true));
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    public void changePassword(String username, String newPassword) throws IOException {
        User user = find(username);
        if (user == null) {
            throw new IllegalArgumentException("No account found for " + username);
        }
        setPassword(user, newPassword, false);
        save();
    }

    /** Resets a student's password back to their roll number and forces a change at next login. */
    public void resetStudentPassword(String rollNo) throws IOException {
        User user = find(rollNo);
        if (user == null) {
            add(newUser(rollNo, "student", rollNo, rollNo, true));
        } else {
            setPassword(user, rollNo, true);
        }
        save();
    }

    public void deleteStudentAccount(String rollNo) throws IOException {
        User user = find(rollNo);
        if (user != null && "student".equals(user.getRole())) {
            users.remove(key(rollNo));
            save();
        }
    }

    // ---------------- helpers ----------------

    private void add(User user) {
        users.put(key(user.getUsername()), user);
    }

    private String key(String username) {
        return username.trim().toLowerCase();
    }

    private User newUser(String username, String role, String rollNo, String password, boolean mustChange) {
        User user = new User(username, role, rollNo, mustChange, "", "");
        setPassword(user, password, mustChange);
        return user;
    }

    private void setPassword(User user, String password, boolean mustChange) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        user.setCredentials(Base64.getEncoder().encodeToString(salt), hash(password, salt), mustChange);
    }

    private String hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing is not available.", e);
        }
    }

    private void load() throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase(HEADER)) {
                continue;
            }
            String[] p = trimmed.split(",", 6);
            if (p.length < 6) {
                continue; // skip malformed lines
            }
            add(new User(p[0], p[1], p[2], Boolean.parseBoolean(p[3]), p[4], p[5]));
        }
    }

    private void save() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (User u : users.values()) {
            lines.add(String.join(",", u.getUsername(), u.getRole(), u.getRollNo(),
                String.valueOf(u.mustChangePassword()), u.getSalt(), u.getPasswordHash()));
        }
        Files.write(path, lines);
    }
}
