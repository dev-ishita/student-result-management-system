package com.srms.model;

/**
 * Represents a single subject and the marks a student scored in it.
 * Fields are private (encapsulated) and only reachable through
 * getters/setters, which validate input before accepting it.
 */
public class Subject {

    private String name;
    private double marksObtained;
    private double maxMarks;

    /**
     * @param name          subject name, e.g. "Mathematics"
     * @param marksObtained marks scored, must be between 0 and maxMarks
     * @param maxMarks      maximum possible marks for this subject
     */
    public Subject(String name, double marksObtained, double maxMarks) {
        setName(name);
        setMaxMarks(maxMarks);
        setMarksObtained(marksObtained);
    }

    /** Convenience constructor assuming a 100-mark subject. */
    public Subject(String name, double marksObtained) {
        this(name, marksObtained, 100.0);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject name cannot be empty.");
        }
        this.name = name.trim();
    }

    public double getMarksObtained() {
        return marksObtained;
    }

    public void setMarksObtained(double marksObtained) {
        if (marksObtained < 0 || marksObtained > maxMarks) {
            throw new IllegalArgumentException(
                "Marks obtained (" + marksObtained + ") must be between 0 and " + maxMarks + "."
            );
        }
        this.marksObtained = marksObtained;
    }

    public double getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(double maxMarks) {
        if (maxMarks <= 0) {
            throw new IllegalArgumentException("Max marks must be greater than 0.");
        }
        this.maxMarks = maxMarks;
    }

    /** Percentage scored in this one subject, e.g. 76.5 */
    public double getPercentage() {
        return (marksObtained / maxMarks) * 100.0;
    }

    @Override
    public String toString() {
        return name + ": " + marksObtained + "/" + maxMarks;
    }
}
