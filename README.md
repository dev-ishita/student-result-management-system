# Student Result Management System

A web-based system that stores student records and subject-wise marks, and automatically calculates total, percentage, grade and pass/fail status. Results are saved to a CSV file, so they persist between runs.

## Features

- Add, update and delete student records (roll number, name, class, subject marks)
- Automatic total, percentage, grade and pass/fail calculation using OOP methods
- Search a result by roll number or name
- Class-wise summary: topper, average percentage and pass percentage
- Data stored in `backend/data/students.csv`

## Tech stack

- **Backend:** Java (plain JDK, no frameworks), using the built-in `com.sun.net.httpserver.HttpServer`
- **Frontend:** HTML, CSS and vanilla JavaScript (`fetch` calls)
- **Storage:** CSV file

## Project structure

```
backend/
  src/com/srms/model/     Student, Subject (encapsulated fields, calculation methods)
  src/com/srms/service/   FileManager (CSV read/write), StudentRepository (in-memory store, auto-saves)
  src/com/srms/server/    Server (HTTP endpoints)
  data/students.csv       persisted student records
frontend/
  index.html              add a student
  search.html             find a result by roll number or name
  dashboard.html          class summary
  css/style.css           shared styling
  js/app.js               calls the backend API
```

## How to run

Requires Java 11 or newer.

1. Start the backend (from the project root):
   ```
   cd backend/src
   javac com/srms/model/*.java com/srms/service/*.java com/srms/server/*.java
   java com.srms.server.Server
   ```
   It prints `Student Result Management System running on http://localhost:8080`.
2. Open `frontend/index.html` in a browser. Keep the backend running while you use it.

## Grading rules

| Percentage | Grade |
|------------|-------|
| 90 and above | A+ |
| 80 - 89 | A |
| 70 - 79 | B+ |
| 60 - 69 | B |
| 50 - 59 | C |
| 40 - 49 | D |
| Below 40 | F |

A student passes only if they score at least 40% in **every** subject. A good overall percentage does not make up for failing one subject.

## API endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/addStudent` | Add a student (form body: `rollNo`, `name`, `className`, and repeated `subjectName`, `marksObtained`, `maxMarks`) |
| PUT | `/updateStudent` | Correct one subject's marks (`rollNo`, `subjectName`, `marksObtained`) |
| DELETE | `/deleteStudent?roll=101` | Delete a student |
| GET | `/getResult?roll=101` or `?name=Ishita` | Look up a result |
| GET | `/classSummary?class=10A` | Topper, average % and pass % for a class |

Responses are JSON. Errors return `{"error": "..."}` with a 400 or 404 status.

## Team roles

This project was built by one person covering all three roles:

- **Backend and OOP architecture:** `model/` and `service/` classes, calculation logic, and the HTTP endpoints in `Server.java`
- **Frontend:** the three HTML pages and `style.css`
- **Integration, Git and QA:** `app.js` connecting the pages to the API, the step-by-step commit history, and manual testing of each endpoint and page

## Possible extensions

Grade-wise colour coding, downloadable report export, teacher/student login, and a bar chart of class performance.
