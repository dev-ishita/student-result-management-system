# Student Result Management System

A system that stores student records, subject-wise marks, and automatically
calculates grades, percentage, and pass/fail status.

## Structure

```
backend/
  src/com/srms/model/    -> Student, Subject (encapsulated data classes)
  src/com/srms/service/  -> file persistence, grade/percentage calculation
  src/com/srms/server/   -> HttpServer, request handlers (API endpoints)
  data/students.csv      -> persisted student records
frontend/
  index.html    -> student entry form
  search.html   -> search a result by roll no / name
  dashboard.html -> class-wise result summary
  css/          -> shared styling
  js/           -> fetch() calls to the backend API
```

## Status

- [x] Step 1: Project structure
- [ ] Step 2: OOP data model (Student, Subject)
- [ ] Step 3: File-based persistence
- [ ] Step 4: Calculation logic (total, percentage, grade)
- [ ] Step 5: HttpServer endpoints
- [ ] Step 6: Frontend pages
- [ ] Step 7: Wire frontend to backend
- [ ] Step 8: Edge case testing

## Running the backend (once built)

```bash
cd backend/src
javac com/srms/model/*.java com/srms/service/*.java com/srms/server/*.java
java com.srms.server.Server
```

Then open `frontend/index.html` in a browser.
