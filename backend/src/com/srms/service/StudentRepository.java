package com.srms.service;

import com.srms.model.Student;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * In-memory store of all students, backed by a CSV file via FileManager.
 * Loads everything into memory once at startup (constructor); every
 * mutating method immediately re-saves to disk so data survives
 * between runs, as the spec requires.
 */
public class StudentRepository {

    private final FileManager fileManager;
    private final Map<String, Student> studentsByRollNo;

    public StudentRepository(String filePath) throws IOException {
        this.fileManager = new FileManager(filePath);
        this.studentsByRollNo = new LinkedHashMap<>();
        for (Student s : fileManager.loadStudents()) {
            studentsByRollNo.put(s.getRollNo(), s);
        }
    }

    public void addStudent(Student student) throws IOException {
        if (studentsByRollNo.containsKey(student.getRollNo())) {
            throw new IllegalArgumentException(
                "A student with roll number " + student.getRollNo() + " already exists."
            );
        }
        studentsByRollNo.put(student.getRollNo(), student);
        persist();
    }

    public void deleteStudent(String rollNo) throws IOException {
        if (studentsByRollNo.remove(rollNo) == null) {
            throw new NoSuchElementException("No student found with roll number " + rollNo);
        }
        persist();
    }

    public Student findByRollNo(String rollNo) {
        return studentsByRollNo.get(rollNo);
    }

    public List<Student> findByName(String name) {
        List<Student> matches = new ArrayList<>();
        for (Student s : studentsByRollNo.values()) {
            if (s.getName().equalsIgnoreCase(name)) {
                matches.add(s);
            }
        }
        return matches;
    }

    public List<Student> findByClassName(String className) {
        List<Student> matches = new ArrayList<>();
        for (Student s : studentsByRollNo.values()) {
            if (s.getClassName().equalsIgnoreCase(className)) {
                matches.add(s);
            }
        }
        return matches;
    }

    public List<Student> getAllStudents() {
        return new ArrayList<>(studentsByRollNo.values());
    }

    /**
     * Call this after mutating a Student object already in the repository
     * directly (e.g. student.addSubject(...) on a student you already
     * fetched) so that change gets written to disk.
     */
    public void persist() throws IOException {
        fileManager.saveStudents(new ArrayList<>(studentsByRollNo.values()));
    }
}
