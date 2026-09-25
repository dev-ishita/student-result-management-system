package com.srms.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single student record: identity fields plus the list
 * of subjects/marks that belong to them.
 *
 * Total/percentage/grade calculation is intentionally NOT here yet —
 * that's added as its own set of methods in the next build step, so
 * this class stays focused purely on holding and validating data.
 */
public class Student {

    private String rollNo;
    private String name;
    private String className;
    private final List<Subject> subjects;

    public Student(String rollNo, String name, String className) {
        setRollNo(rollNo);
        setName(name);
        setClassName(className);
        this.subjects = new ArrayList<>();
    }

    public String getRollNo() {
        return rollNo;
    }

    private void setRollNo(String rollNo) {
        if (rollNo == null || rollNo.trim().isEmpty()) {
            throw new IllegalArgumentException("Roll number cannot be empty.");
        }
        this.rollNo = rollNo.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty.");
        }
        this.name = name.trim();
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be empty.");
        }
        this.className = className.trim();
    }

    /**
     * Adds a subject's marks to this student. Rejects a duplicate
     * subject name so the same subject can't accidentally be added twice —
     * use updateSubject() instead if the intent is to correct a mark.
     */
    public void addSubject(Subject subject) {
        if (subject == null) {
            throw new IllegalArgumentException("Subject cannot be null.");
        }
        boolean alreadyExists = subjects.stream()
            .anyMatch(s -> s.getName().equalsIgnoreCase(subject.getName()));
        if (alreadyExists) {
            throw new IllegalArgumentException(
                "Subject '" + subject.getName() + "' already exists for this student. Use updateSubject() instead."
            );
        }
        subjects.add(subject);
    }

    /** Replaces the marks for an existing subject (matched by name, case-insensitive). */
    public void updateSubject(String subjectName, double newMarksObtained) {
        for (Subject s : subjects) {
            if (s.getName().equalsIgnoreCase(subjectName)) {
                s.setMarksObtained(newMarksObtained);
                return;
            }
        }
        throw new IllegalArgumentException("Subject '" + subjectName + "' not found for this student.");
    }

    public void removeSubject(String subjectName) {
        boolean removed = subjects.removeIf(s -> s.getName().equalsIgnoreCase(subjectName));
        if (!removed) {
            throw new IllegalArgumentException("Subject '" + subjectName + "' not found for this student.");
        }
    }

    /**
     * Returns an unmodifiable view of the subject list — callers can read
     * it but can't add/remove entries directly, preserving encapsulation.
     * Use addSubject()/updateSubject()/removeSubject() to make changes.
     */
    public List<Subject> getSubjects() {
        return Collections.unmodifiableList(subjects);
    }

    @Override
    public String toString() {
        return "Student{" +
            "rollNo='" + rollNo + '\'' +
            ", name='" + name + '\'' +
            ", className='" + className + '\'' +
            ", subjects=" + subjects +
            '}';
    }
}
