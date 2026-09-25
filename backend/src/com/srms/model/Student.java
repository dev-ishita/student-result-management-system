package com.srms.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single student record: identity fields, the list of
 * subjects/marks that belong to them, and the OOP methods that turn
 * those marks into a total, percentage, letter grade, and pass/fail
 * status.
 */
public class Student {

    /** Minimum percentage required in a single subject to pass it. */
    private static final double PASS_THRESHOLD_PERCENTAGE = 40.0;

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

    // ---- Calculation methods (OOP methods, as the spec asks for) ----

    /** Sum of marks obtained across every subject. */
    public double getTotalMarksObtained() {
        double total = 0;
        for (Subject s : subjects) {
            total += s.getMarksObtained();
        }
        return total;
    }

    /** Sum of the maximum possible marks across every subject. */
    public double getTotalMaxMarks() {
        double total = 0;
        for (Subject s : subjects) {
            total += s.getMaxMarks();
        }
        return total;
    }

    /** Overall percentage across all subjects. Returns 0 if no subjects have been added yet. */
    public double getPercentage() {
        double maxTotal = getTotalMaxMarks();
        if (maxTotal == 0) {
            return 0.0;
        }
        return (getTotalMarksObtained() / maxTotal) * 100.0;
    }

    /** Letter grade derived from overall percentage. */
    public String getGrade() {
        double pct = getPercentage();
        if (pct >= 90) return "A+";
        if (pct >= 80) return "A";
        if (pct >= 70) return "B+";
        if (pct >= 60) return "B";
        if (pct >= 50) return "C";
        if (pct >= 40) return "D";
        return "F";
    }

    /**
     * A student passes only if they've cleared the pass threshold in
     * EVERY subject individually — a strong overall percentage doesn't
     * make up for failing one subject. Returns false if no subjects
     * have been recorded yet.
     */
    public boolean isPass() {
        if (subjects.isEmpty()) {
            return false;
        }
        for (Subject s : subjects) {
            if (s.getPercentage() < PASS_THRESHOLD_PERCENTAGE) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Student{" +
            "rollNo='" + rollNo + '\'' +
            ", name='" + name + '\'' +
            ", className='" + className + '\'' +
            ", subjects=" + subjects +
            ", percentage=" + String.format("%.2f", getPercentage()) +
            ", grade='" + getGrade() + '\'' +
            ", pass=" + isPass() +
            '}';
    }
}
