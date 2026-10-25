# Student Result Management System

A web-based system that stores student records and subject-wise marks, and automatically calculates total, percentage, grade and pass/fail status. Results are saved to CSV files, so they persist between runs.

## Features

- Add, update and delete student records (roll number, name, class, subject marks)
- Automatic total, percentage, grade and pass/fail calculation using OOP methods
- Search a result by roll number or name
- Class-wise summary: topper, average percentage and pass percentage, with a bar chart and a downloadable CSV report
- Results colour-coded: pass in green, fail in red
- Bulk import of students from an Excel or CSV sheet
- Login with two roles. Teachers manage everything. Each student has their own account and can only see their own result.

## Tech stack

- **Backend:** Java (plain JDK, no frameworks), using the built-in `com.sun.net.httpserver.HttpServer`
- **Frontend:** HTML, CSS and vanilla JavaScript (`fetch` calls), Chart.js for the chart and SheetJS for reading spreadsheets (both loaded from a CDN)
- **Storage:** CSV files (`students.csv` and `users.csv`)

## Project structure

```
backend/
  src/com/srms/model/     Student, Subject, User (encapsulated fields, calculation methods)
  src/com/srms/service/   FileManager and StudentRepository (student CSV), UserRepository (accounts, hashed passwords)
  src/com/srms/server/    Server (HTTP endpoints, login and role checks)
  data/students.csv       persisted student records
  data/users.csv          login accounts (created on first run, not committed to git)
frontend/
  login.html              log in
  change-password.html    change password (forced on first login)
  index.html              add a student (teachers)
  import.html             import students from a spreadsheet (teachers)
  search.html             find a result (teachers search, students see their own)
  dashboard.html          class summary, chart and report (teachers)
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
2. Open `frontend/login.html` in a browser and log in. Keep the backend running while you use it.

## Login accounts

| Role | Username | First password |
|------|----------|----------------|
| Teacher | `teacher` | `teacher123` |
| Student | their roll number | their roll number |

- Every student gets an account automatically when they are added or imported, and existing students get one the first time the server starts.
- A student must change the default password the first time they log in.
- A teacher can reset a student's password back to their roll number from the result search page.
- Passwords are stored as salted PBKDF2 hashes, never as plain text.
- Roles are enforced by the backend, not just hidden in the interface. Logins last until the server restarts.
- This runs over plain HTTP on one machine. A real deployment would need HTTPS.

## Importing from Excel

The first row must have **Roll No**, **Name** and **Class**, then one column per subject. Subjects are out of 100 unless the header says otherwise, for example `Chemistry (50)`. Leave a mark blank if a student has none for that subject. The import page has a **Download template** button and shows a preview before anything is saved. Rows with problems, or roll numbers that already exist, are skipped and listed in a report.

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

Every endpoint except `/login` needs an `Authorization: Bearer <token>` header. Anything not allowed returns 401 (not logged in) or 403 (wrong role, or password change pending).

| Method | Path | Who | Purpose |
|--------|------|-----|---------|
| POST | `/login` | anyone | Log in (`username`, `password`); returns a token and role |
| POST | `/changePassword` | any logged-in user | Change own password (`currentPassword`, `newPassword`) |
| POST | `/resetPassword` | teacher | Reset a student's password to their roll number (`rollNo`) |
| POST | `/addStudent` | teacher | Add a student (`rollNo`, `name`, `className`, repeated `subjectName`, `marksObtained`, `maxMarks`) |
| POST | `/importStudents` | teacher | Add many students at once from the import page |
| PUT | `/updateStudent` | teacher | Correct one subject's marks (`rollNo`, `subjectName`, `marksObtained`) |
| DELETE | `/deleteStudent?roll=101` | teacher | Delete a student and their account |
| GET | `/getResult?roll=101` or `?name=Ishita` | teacher, student | Look up a result (students always get their own) |
| GET | `/classSummary?class=10A` | teacher | Topper, average % and pass % for a class |

Responses are JSON. Errors return `{"error": "..."}`.

## Team roles

This project was built by one person covering all three roles:

- **Backend and OOP architecture:** `model/` and `service/` classes, calculation logic, accounts and the HTTP endpoints in `Server.java`
- **Frontend:** the HTML pages and `style.css`
- **Integration, Git and QA:** `app.js` connecting the pages to the API, the step-by-step commit history, and manual testing of each endpoint and page

## Possible extensions

Moving from CSV files to a database such as SQLite for larger colleges, HTTPS, and grade-wise colour shades.
