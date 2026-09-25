package com.srms.service;

import com.srms.model.Student;
import com.srms.model.Subject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles reading and writing student records to/from a CSV file.
 * This class knows nothing about validation or business rules -
 * it just converts Student objects to/from CSV lines.
 *
 * CSV format per line:
 *   rollNo,name,className,subjectName:marks:maxMarks;subjectName:marks:maxMarks;...
 */
public class FileManager {

    private static final String HEADER = "rollNo,name,className,subjectMarks";
    private final String filePath;

    public FileManager(String filePath) {
        this.filePath = filePath;
    }

    /** Reads every student record from the CSV file. Returns an empty list if the file doesn't exist yet. */
    public List<Student> loadStudents() throws IOException {
        List<Student> students = new ArrayList<>();
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return students;
        }

        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase(HEADER)) {
                continue; // skip blank lines and the header row
            }
            students.add(deserializeStudent(trimmed));
        }
        return students;
    }

    /** Overwrites the CSV file with the full given list of students (header included). */
    public void saveStudents(List<Student> students) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Student s : students) {
            lines.add(serializeStudent(s));
        }
        Files.write(Paths.get(filePath), lines);
    }

    private String serializeStudent(Student s) {
        StringBuilder subjectsPart = new StringBuilder();
        List<Subject> subjects = s.getSubjects();
        for (int i = 0; i < subjects.size(); i++) {
            Subject sub = subjects.get(i);
            subjectsPart.append(sub.getName()).append(':')
                        .append(sub.getMarksObtained()).append(':')
                        .append(sub.getMaxMarks());
            if (i < subjects.size() - 1) {
                subjectsPart.append(';');
            }
        }
        return String.join(",", s.getRollNo(), s.getName(), s.getClassName(), subjectsPart.toString());
    }

    private Student deserializeStudent(String line) {
        // Limit to 4 parts so the subjects section (which uses ; and : internally, never ,) stays intact
        String[] parts = line.split(",", 4);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Malformed student record in CSV: " + line);
        }

        Student student = new Student(parts[0], parts[1], parts[2]);

        if (parts.length == 4 && !parts[3].isEmpty()) {
            for (String entry : parts[3].split(";")) {
                String[] subjectFields = entry.split(":");
                if (subjectFields.length == 3) {
                    double marks = Double.parseDouble(subjectFields[1]);
                    double max = Double.parseDouble(subjectFields[2]);
                    student.addSubject(new Subject(subjectFields[0], marks, max));
                }
            }
        }
        return student;
    }
}
