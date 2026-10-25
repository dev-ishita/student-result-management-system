package com.srms.server;

import com.srms.model.Student;
import com.srms.model.Subject;
import com.srms.model.User;
import com.srms.service.StudentRepository;
import com.srms.service.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Exposes the Student Result Management System over a small JSON/HTTP API
 * using the JDK's built-in HttpServer - no external frameworks needed.
 *
 * Open to anyone:
 *   POST   /login            - log in (form body: username, password); returns a token
 *
 * Any logged-in user:
 *   POST   /changePassword   - change own password (currentPassword, newPassword)
 *
 * Teachers only:
 *   POST   /addStudent       - add a student (rollNo, name, className, repeated subjectName/marksObtained/maxMarks)
 *   POST   /importStudents   - add many students at once (parallel lists: row, rollNo, name, className, subjects)
 *   PUT    /updateStudent    - correct one subject's marks (rollNo, subjectName, marksObtained)
 *   DELETE /deleteStudent    - remove a student and their account (query param: roll)
 *   GET    /classSummary     - topper, average %, pass % for a class (query param: class)
 *   POST   /resetPassword    - reset a student's password to their roll number (rollNo)
 *
 * Teachers and students:
 *   GET    /getResult        - teachers look up by roll or name; students always get their own result
 *
 * Every endpoint except /login needs an "Authorization: Bearer <token>" header.
 */
public class Server {

    private static final int PORT = 8080;
    private static final String DEFAULT_DATA_FILE = "../data/students.csv";
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final StudentRepository repository;
    private final UserRepository users;
    /** Active logins: token -> username. Held in memory, so restarting the server logs everyone out. */
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public Server(StudentRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    public static void main(String[] args) throws IOException {
        String dataFile = args.length > 0 ? args[0] : DEFAULT_DATA_FILE;
        StudentRepository repository = new StudentRepository(dataFile);
        String usersFile = Paths.get(dataFile).resolveSibling("users.csv").toString();
        UserRepository users = new UserRepository(usersFile);

        // Make sure every existing student has an account (username and first password = roll number).
        List<String> rollNos = repository.getAllStudents().stream()
            .map(Student::getRollNo)
            .collect(Collectors.toList());
        users.ensureStudentAccounts(rollNos);

        new Server(repository, users).start();
    }

    public void start() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(PORT), 0);
        httpServer.createContext("/login", this::handleLogin);
        httpServer.createContext("/changePassword", this::handleChangePassword);
        httpServer.createContext("/resetPassword", this::handleResetPassword);
        httpServer.createContext("/addStudent", this::handleAddStudent);
        httpServer.createContext("/importStudents", this::handleImportStudents);
        httpServer.createContext("/updateStudent", this::handleUpdateStudent);
        httpServer.createContext("/deleteStudent", this::handleDeleteStudent);
        httpServer.createContext("/getResult", this::handleGetResult);
        httpServer.createContext("/classSummary", this::handleClassSummary);
        httpServer.setExecutor(null);
        httpServer.start();
        System.out.println("Student Result Management System running on http://localhost:" + PORT);
    }

    // ---------------- Login and accounts ----------------

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only POST is allowed on /login"));
            return;
        }
        try {
            Map<String, List<String>> form = parseFormBody(exchange);
            User user = users.authenticate(firstValue(form, "username").trim(), firstValue(form, "password"));
            if (user == null) {
                sendJson(exchange, 401, errorJson("Incorrect username or password."));
                return;
            }
            String token = UUID.randomUUID().toString();
            sessions.put(token, user.getUsername());
            sendJson(exchange, 200,
                "{\"token\":\"" + token + "\"," +
                "\"role\":\"" + user.getRole() + "\"," +
                "\"username\":\"" + escapeJson(user.getUsername()) + "\"," +
                "\"mustChangePassword\":" + user.mustChangePassword() + "}");
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        }
    }

    private void handleChangePassword(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        User user = authenticateUser(exchange);   // allowed even while a password change is pending
        if (user == null) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only POST is allowed on /changePassword"));
            return;
        }
        try {
            Map<String, List<String>> form = parseFormBody(exchange);
            String current = firstValue(form, "currentPassword");
            String next = firstValue(form, "newPassword");

            if (users.authenticate(user.getUsername(), current) == null) {
                sendJson(exchange, 400, errorJson("Your current password is incorrect."));
                return;
            }
            if (next.length() < MIN_PASSWORD_LENGTH) {
                sendJson(exchange, 400, errorJson("The new password must be at least " + MIN_PASSWORD_LENGTH + " characters."));
                return;
            }
            if (next.equals(current)) {
                sendJson(exchange, 400, errorJson("The new password must be different from the current one."));
                return;
            }
            users.changePassword(user.getUsername(), next);
            sendJson(exchange, 200, "{\"message\":\"Password changed.\"}");
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    private void handleResetPassword(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        if (authorize(exchange, "teacher") == null) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only POST is allowed on /resetPassword"));
            return;
        }
        try {
            String rollNo = firstValue(parseFormBody(exchange), "rollNo");
            if (repository.findByRollNo(rollNo) == null) {
                sendJson(exchange, 404, errorJson("No student found with roll number " + rollNo));
                return;
            }
            users.resetStudentPassword(rollNo);
            sendJson(exchange, 200, "{\"message\":\"Password for roll " + escapeJson(rollNo) +
                " reset to their roll number. They will be asked to change it at their next login.\"}");
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    // ---------------- Student handlers ----------------

    private void handleAddStudent(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        if (authorize(exchange, "teacher") == null) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only POST is allowed on /addStudent"));
            return;
        }
        try {
            Map<String, List<String>> form = parseFormBody(exchange);
            Student student = new Student(
                clean(firstValue(form, "rollNo")),
                clean(firstValue(form, "name")),
                clean(firstValue(form, "className")));

            List<String> subjectNames = form.getOrDefault("subjectName", Collections.emptyList());
            List<String> marksList = form.getOrDefault("marksObtained", Collections.emptyList());
            List<String> maxMarksList = form.getOrDefault("maxMarks", Collections.emptyList());

            for (int i = 0; i < subjectNames.size(); i++) {
                double marks = Double.parseDouble(marksList.get(i));
                double max = (i < maxMarksList.size()) ? Double.parseDouble(maxMarksList.get(i)) : 100.0;
                student.addSubject(new Subject(cleanSubject(subjectNames.get(i)), marks, max));
            }

            repository.addStudent(student);
            users.createStudentAccount(student.getRollNo());
            sendJson(exchange, 201, studentResultJson(student));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Bulk import. The browser reads the spreadsheet and sends one entry per
     * student in five parallel lists: row, rollNo, name, className, subjects,
     * where subjects looks like "Maths:85:100;Physics:78:100".
     * Students that can't be added (e.g. duplicate roll number) are skipped
     * and reported back, the rest are added.
     */
    private void handleImportStudents(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        if (authorize(exchange, "teacher") == null) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only POST is allowed on /importStudents"));
            return;
        }
        try {
            Map<String, List<String>> form = parseFormBody(exchange);
            List<String> rows = form.getOrDefault("row", Collections.emptyList());
            List<String> rolls = form.getOrDefault("rollNo", Collections.emptyList());
            List<String> names = form.getOrDefault("name", Collections.emptyList());
            List<String> classes = form.getOrDefault("className", Collections.emptyList());
            List<String> subjectLists = form.getOrDefault("subjects", Collections.emptyList());

            int count = rolls.size();
            if (rows.size() != count || names.size() != count || classes.size() != count || subjectLists.size() != count) {
                sendJson(exchange, 400, errorJson("The import data is incomplete. Please try again."));
                return;
            }

            int added = 0;
            List<String> skipped = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                try {
                    Student student = new Student(clean(rolls.get(i)), clean(names.get(i)), clean(classes.get(i)));
                    for (String entry : subjectLists.get(i).split(";")) {
                        if (entry.trim().isEmpty()) continue;
                        String[] parts = entry.split(":");
                        if (parts.length != 3) {
                            throw new IllegalArgumentException("Subject entry '" + entry + "' is not valid.");
                        }
                        student.addSubject(new Subject(
                            cleanSubject(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
                    }
                    repository.addStudent(student);
                    users.createStudentAccount(student.getRollNo());
                    added++;
                } catch (IllegalArgumentException e) {
                    skipped.add("{\"row\":\"" + escapeJson(rows.get(i)) + "\"," +
                        "\"rollNo\":\"" + escapeJson(rolls.get(i)) + "\"," +
                        "\"reason\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
            sendJson(exchange, 200, "{\"added\":" + added + ",\"skipped\":[" + String.join(",", skipped) + "]}");
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    private void handleUpdateStudent(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        if (authorize(exchange, "teacher") == null) return;
        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only PUT is allowed on /updateStudent"));
            return;
        }
        try {
            Map<String, List<String>> form = parseFormBody(exchange);
            String rollNo = firstValue(form, "rollNo");
            String subjectName = firstValue(form, "subjectName");
            double marks = Double.parseDouble(firstValue(form, "marksObtained"));

            Student student = repository.findByRollNo(rollNo);
            if (student == null) {
                sendJson(exchange, 404, errorJson("No student found with roll number " + rollNo));
                return;
            }
            student.updateSubject(subjectName, marks);
            repository.persist();
            sendJson(exchange, 200, studentResultJson(student));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    private void handleDeleteStudent(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        if (authorize(exchange, "teacher") == null) return;
        if (!"DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only DELETE is allowed on /deleteStudent"));
            return;
        }
        try {
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String rollNo = query.get("roll");
            if (rollNo == null || rollNo.isEmpty()) {
                sendJson(exchange, 400, errorJson("Missing 'roll' query parameter."));
                return;
            }
            repository.deleteStudent(rollNo);
            users.deleteStudentAccount(rollNo);
            sendJson(exchange, 200, "{\"message\":\"Student " + escapeJson(rollNo) + " deleted.\"}");
        } catch (NoSuchElementException e) {
            sendJson(exchange, 404, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    private void handleGetResult(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        User user = authorize(exchange, "teacher", "student");
        if (user == null) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only GET is allowed on /getResult"));
            return;
        }
        try {
            // A student can only ever see their own result, whatever they ask for.
            if ("student".equals(user.getRole())) {
                Student own = repository.findByRollNo(user.getRollNo());
                if (own == null) {
                    sendJson(exchange, 404, errorJson("No result has been recorded for your roll number yet."));
                } else {
                    sendJson(exchange, 200, studentResultJson(own));
                }
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String roll = query.get("roll");
            String name = query.get("name");

            if (roll != null && !roll.isEmpty()) {
                Student student = repository.findByRollNo(roll);
                if (student == null) {
                    sendJson(exchange, 404, errorJson("No student found with roll number " + roll));
                    return;
                }
                sendJson(exchange, 200, studentResultJson(student));
            } else if (name != null && !name.isEmpty()) {
                List<Student> matches = repository.findByName(name);
                if (matches.isEmpty()) {
                    sendJson(exchange, 404, errorJson("No student found with name " + name));
                    return;
                }
                String json = "[" + matches.stream().map(this::studentResultJson).collect(Collectors.joining(",")) + "]";
                sendJson(exchange, 200, json);
            } else {
                sendJson(exchange, 400, errorJson("Provide a 'roll' or 'name' query parameter."));
            }
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    private void handleClassSummary(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        if (authorize(exchange, "teacher") == null) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only GET is allowed on /classSummary"));
            return;
        }
        try {
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String className = query.get("class");
            if (className == null || className.isEmpty()) {
                sendJson(exchange, 400, errorJson("Provide a 'class' query parameter."));
                return;
            }
            List<Student> students = repository.findByClassName(className);
            if (students.isEmpty()) {
                sendJson(exchange, 404, errorJson("No students found in class " + className));
                return;
            }

            Student topper = students.get(0);
            double totalPercentage = 0;
            int passCount = 0;
            for (Student s : students) {
                if (s.getPercentage() > topper.getPercentage()) {
                    topper = s;
                }
                totalPercentage += s.getPercentage();
                if (s.isPass()) passCount++;
            }
            double averagePercentage = totalPercentage / students.size();
            double passPercentage = (passCount * 100.0) / students.size();

            String json = "{" +
                "\"className\":\"" + escapeJson(className) + "\"," +
                "\"studentCount\":" + students.size() + "," +
                "\"averagePercentage\":" + round2(averagePercentage) + "," +
                "\"passPercentage\":" + round2(passPercentage) + "," +
                "\"topper\":" + studentResultJson(topper) + "," +
                "\"students\":[" + students.stream().map(this::studentResultJson).collect(Collectors.joining(",")) + "]" +
                "}";

            sendJson(exchange, 200, json);
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    // ---------------- Login checks ----------------

    /**
     * Finds the logged-in user from the "Authorization: Bearer <token>" header.
     * Sends a 401 and returns null if there isn't one.
     */
    private User authenticateUser(HttpExchange exchange) throws IOException {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        User user = null;
        if (header != null && header.startsWith("Bearer ")) {
            String username = sessions.get(header.substring(7).trim());
            if (username != null) {
                user = users.find(username);
            }
        }
        if (user == null) {
            sendJson(exchange, 401, errorJson("Please log in first."));
        }
        return user;
    }

    /**
     * Like authenticateUser, but also requires the password to have been
     * changed from its default and the user's role to be one of allowedRoles.
     * Sends a 401/403 and returns null if the request isn't allowed.
     */
    private User authorize(HttpExchange exchange, String... allowedRoles) throws IOException {
        User user = authenticateUser(exchange);
        if (user == null) {
            return null;
        }
        if (user.mustChangePassword()) {
            sendJson(exchange, 403, errorJson("Please change your password first."));
            return null;
        }
        for (String allowed : allowedRoles) {
            if (allowed.equals(user.getRole())) {
                return user;
            }
        }
        sendJson(exchange, 403, errorJson("Your account is not allowed to do this."));
        return null;
    }

    // ---------------- JSON building ----------------

    private String studentResultJson(Student s) {
        List<Subject> subjects = s.getSubjects();
        String subjectsJson = subjects.stream().map(sub ->
            "{\"name\":\"" + escapeJson(sub.getName()) + "\"," +
            "\"marksObtained\":" + sub.getMarksObtained() + "," +
            "\"maxMarks\":" + sub.getMaxMarks() + "}"
        ).collect(Collectors.joining(",", "[", "]"));

        return "{" +
            "\"rollNo\":\"" + escapeJson(s.getRollNo()) + "\"," +
            "\"name\":\"" + escapeJson(s.getName()) + "\"," +
            "\"className\":\"" + escapeJson(s.getClassName()) + "\"," +
            "\"subjects\":" + subjectsJson + "," +
            "\"totalMarksObtained\":" + s.getTotalMarksObtained() + "," +
            "\"totalMaxMarks\":" + s.getTotalMaxMarks() + "," +
            "\"percentage\":" + round2(s.getPercentage()) + "," +
            "\"grade\":\"" + s.getGrade() + "\"," +
            "\"pass\":" + s.isPass() +
            "}";
    }

    private String errorJson(String message) {
        return "{\"error\":\"" + escapeJson(message) + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** The CSV files use commas as separators, so commas and line breaks can't appear inside a value. */
    private String clean(String value) {
        return value.replace(',', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    /** Subject names are also packed as name:marks:max;... so ':' and ';' are removed too. */
    private String cleanSubject(String value) {
        return clean(value).replace(':', ' ').replace(';', ' ').trim();
    }

    // ---------------- HTTP plumbing ----------------

    /**
     * Adds CORS headers to every response and answers OPTIONS preflight
     * requests directly. Returns true if this request WAS a preflight
     * (caller should stop and not run any business logic).
     */
    private boolean handleCorsAndPreflight(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, List<String>> parseFormBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (body.isEmpty()) return result;
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return result;
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private String firstValue(Map<String, List<String>> form, String key) {
        List<String> values = form.get(key);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return values.get(0);
    }
}
