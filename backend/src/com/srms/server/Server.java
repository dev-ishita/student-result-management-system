package com.srms.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.srms.model.Student;
import com.srms.model.Subject;
import com.srms.service.StudentRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Exposes the Student Result Management System over a small JSON/HTTP API
 * using the JDK's built-in HttpServer - no external frameworks needed.
 *
 * Endpoints:
 *   POST   /addStudent     - add a new student (form body: rollNo, name, className,
 *                             repeated subjectName/marksObtained/maxMarks triplets)
 *   PUT    /updateStudent  - correct one subject's marks (form body: rollNo, subjectName, marksObtained)
 *   DELETE /deleteStudent  - remove a student (query param: roll)
 *   GET    /getResult      - look up a result (query param: roll OR name)
 *   GET    /classSummary   - class-wise summary: topper, average %, pass % (query param: class)
 */
public class Server {

    private static final int PORT = 8080;
    private static final String DEFAULT_DATA_FILE = "../data/students.csv";

    private final StudentRepository repository;

    public Server(StudentRepository repository) {
        this.repository = repository;
    }

    public static void main(String[] args) throws IOException {
        String dataFile = args.length > 0 ? args[0] : DEFAULT_DATA_FILE;
        StudentRepository repository = new StudentRepository(dataFile);
        new Server(repository).start();
    }

    public void start() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(PORT), 0);
        httpServer.createContext("/addStudent", this::handleAddStudent);
        httpServer.createContext("/updateStudent", this::handleUpdateStudent);
        httpServer.createContext("/deleteStudent", this::handleDeleteStudent);
        httpServer.createContext("/getResult", this::handleGetResult);
        httpServer.createContext("/classSummary", this::handleClassSummary);
        httpServer.setExecutor(null);
        httpServer.start();
        System.out.println("Student Result Management System running on http://localhost:" + PORT);
    }

    // ---------------- Handlers ----------------

    private void handleAddStudent(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only POST is allowed on /addStudent"));
            return;
        }
        try {
            Map<String, List<String>> form = parseFormBody(exchange);
            Student student = new Student(firstValue(form, "rollNo"), firstValue(form, "name"), firstValue(form, "className"));

            List<String> subjectNames = form.getOrDefault("subjectName", Collections.emptyList());
            List<String> marksList = form.getOrDefault("marksObtained", Collections.emptyList());
            List<String> maxMarksList = form.getOrDefault("maxMarks", Collections.emptyList());

            for (int i = 0; i < subjectNames.size(); i++) {
                double marks = Double.parseDouble(marksList.get(i));
                double max = (i < maxMarksList.size()) ? Double.parseDouble(maxMarksList.get(i)) : 100.0;
                student.addSubject(new Subject(subjectNames.get(i), marks, max));
            }

            repository.addStudent(student);
            sendJson(exchange, 201, studentResultJson(student));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    private void handleUpdateStudent(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
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
            sendJson(exchange, 200, "{\"message\":\"Student " + escapeJson(rollNo) + " deleted.\"}");
        } catch (NoSuchElementException e) {
            sendJson(exchange, 404, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Unexpected error: " + e.getMessage()));
        }
    }

    private void handleGetResult(HttpExchange exchange) throws IOException {
        if (handleCorsAndPreflight(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson("Only GET is allowed on /getResult"));
            return;
        }
        try {
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
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

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
