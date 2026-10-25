// Shared script for every page. Each block only runs if its page's elements exist.
const API = "http://localhost:8080";
const PASS_MARK = 40; // minimum % per subject; keep in step with PASS_THRESHOLD_PERCENTAGE in Student.java

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function showMessage(text, type) {
  const box = document.getElementById("message");
  if (!box) return;
  box.textContent = text;
  box.className = "message show " + type;
}

function clearMessage() {
  const box = document.getElementById("message");
  if (box) box.className = "message";
}

const FORM_HEADERS = { "Content-Type": "application/x-www-form-urlencoded" };

// Calls the backend and returns the parsed JSON. Throws an Error with a readable message on failure.
async function callApi(path, options) {
  options = options || {};
  const headers = Object.assign({}, options.headers);
  const token = sessionStorage.getItem("token");
  if (token) headers["Authorization"] = "Bearer " + token;

  let response;
  try {
    response = await fetch(API + path, Object.assign({}, options, { headers }));
  } catch (e) {
    throw new Error("Can't reach the server. Start it with: java com.srms.server.Server");
  }
  const data = await response.json();
  if (response.status === 401 && path !== "/login") {
    // Session expired (e.g. the server was restarted): send the user back to log in.
    sessionStorage.clear();
    location.replace("login.html");
  }
  if (!response.ok) throw new Error(data.error || "Something went wrong.");
  return data;
}

function resultText(student) {
  return student.pass
    ? '<span class="pass">Pass</span>'
    : '<span class="fail">Fail</span>';
}

/* ---------- Login state, page guards and navigation ---------- */
const page = location.pathname.split("/").pop();
const role = sessionStorage.getItem("role");
const mustChange = sessionStorage.getItem("mustChange") === "true";
const TEACHER_PAGES = ["index.html", "dashboard.html", "import.html"];

function makeLink(href, text) {
  const a = document.createElement("a");
  a.href = href;
  a.textContent = text;
  if (href === page) a.className = "active";
  return a;
}

function buildNav() {
  const nav = document.querySelector("nav");
  if (!nav || !role) return;
  if (mustChange) {
    nav.querySelectorAll("a").forEach(a => a.remove()); // must change the password before going anywhere
  } else {
    if (role === "student") {
      nav.querySelectorAll('a[href="index.html"], a[href="dashboard.html"], a[href="import.html"]')
        .forEach(a => a.remove());
    }
    if (role === "teacher" && !nav.querySelector('a[href="import.html"]')) {
      nav.appendChild(makeLink("import.html", "Import students"));
    }
    if (!nav.querySelector('a[href="change-password.html"]')) {
      nav.appendChild(makeLink("change-password.html", "Change password"));
    }
  }
  const logout = makeLink("login.html", "Log out (" + (sessionStorage.getItem("username") || role) + ")");
  logout.addEventListener("click", () => sessionStorage.clear());
  nav.appendChild(logout);
}

if (page !== "login.html") {
  if (!role) {
    location.replace("login.html");
  } else if (mustChange && page !== "change-password.html") {
    location.replace("change-password.html");
  } else if (role === "student" && TEACHER_PAGES.includes(page)) {
    location.replace("search.html"); // students can only look up their own result
  }
  buildNav();
}

/* ---------- Login page ---------- */
const loginForm = document.getElementById("login-form");
if (loginForm) {
  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearMessage();
    const body = new URLSearchParams();
    body.append("username", document.getElementById("username").value.trim());
    body.append("password", document.getElementById("password").value);
    try {
      const data = await callApi("/login", { method: "POST", headers: FORM_HEADERS, body: body.toString() });
      sessionStorage.setItem("token", data.token);
      sessionStorage.setItem("role", data.role);
      sessionStorage.setItem("username", data.username);
      sessionStorage.setItem("mustChange", String(data.mustChangePassword));
      if (data.mustChangePassword) location.replace("change-password.html");
      else location.replace(data.role === "teacher" ? "index.html" : "search.html");
    } catch (e) {
      showMessage(e.message, "error");
    }
  });
}

/* ---------- Change password page ---------- */
const changeForm = document.getElementById("change-form");
if (changeForm) {
  if (mustChange) document.getElementById("first-login-note").hidden = false;

  changeForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearMessage();
    const next = document.getElementById("new-password").value;
    if (next !== document.getElementById("confirm-password").value) {
      showMessage("The new password and its confirmation don't match.", "error");
      return;
    }
    const body = new URLSearchParams();
    body.append("currentPassword", document.getElementById("current-password").value);
    body.append("newPassword", next);
    try {
      await callApi("/changePassword", { method: "POST", headers: FORM_HEADERS, body: body.toString() });
      sessionStorage.setItem("mustChange", "false");
      showMessage("Password changed. Taking you to the app...", "ok");
      setTimeout(() => location.replace(role === "teacher" ? "index.html" : "search.html"), 1200);
    } catch (e) {
      showMessage(e.message, "error");
    }
  });
}

/* ---------- Page 1: add student ---------- */
const studentForm = document.getElementById("student-form");
if (studentForm) {
  const subjects = document.getElementById("subjects");
  const addBtn = document.getElementById("add-subject");

  addBtn.addEventListener("click", () => {
    const row = subjects.firstElementChild.cloneNode(true);
    row.querySelectorAll("input").forEach(input => {
      input.value = input.name === "maxMarks" ? "100" : "";
    });
    subjects.appendChild(row);
    row.querySelector("input").focus();
  });

  const removeBtn = document.createElement("button");
  removeBtn.type = "button";
  removeBtn.className = "secondary";
  removeBtn.textContent = "Remove last subject";
  removeBtn.addEventListener("click", () => {
    if (subjects.children.length > 1) subjects.lastElementChild.remove();
  });
  addBtn.after(removeBtn);

  studentForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearMessage();

    const body = new URLSearchParams();
    body.append("rollNo", document.getElementById("rollNo").value);
    body.append("name", document.getElementById("name").value);
    body.append("className", document.getElementById("className").value);
    subjects.querySelectorAll(".subject-row").forEach(row => {
      body.append("subjectName", row.querySelector('[name="subjectName"]').value);
      body.append("marksObtained", row.querySelector('[name="marksObtained"]').value);
      body.append("maxMarks", row.querySelector('[name="maxMarks"]').value);
    });

    try {
      const s = await callApi("/addStudent", { method: "POST", headers: FORM_HEADERS, body: body.toString() });
      showMessage(
        `Saved ${s.name} (roll ${s.rollNo}): ${s.percentage}%, grade ${s.grade}, ${s.pass ? "pass" : "fail"}. ` +
        "Their login is their roll number, which is also their first password.", "ok");
      studentForm.reset();
      while (subjects.children.length > 1) subjects.lastElementChild.remove();
    } catch (e) {
      showMessage(e.message, "error");
    }
  });
}

/* ---------- Page 2: find a result ---------- */
const searchForm = document.getElementById("search-form");
if (searchForm) {
  const results = document.getElementById("results");

  function renderStudent(s) {
    const cls = s.pass ? "pass" : "fail";
    const rows = s.subjects.map(sub => {
      const subjectPass = sub.maxMarks > 0 && (sub.marksObtained / sub.maxMarks) * 100 >= PASS_MARK;
      return `<tr><td>${escapeHtml(sub.name)}</td>` +
        `<td class="${subjectPass ? "pass" : "fail"}">${sub.marksObtained} / ${sub.maxMarks}</td></tr>`;
    }).join("");
    const reset = role === "teacher"
      ? `<div class="actions"><button type="button" class="secondary reset-password" data-roll="${escapeHtml(s.rollNo)}">Reset password</button></div>`
      : "";
    return `
      <div class="card result-${cls}">
        <h2>${escapeHtml(s.name)}</h2>
        <p class="lead">Roll ${escapeHtml(s.rollNo)} &middot; Class ${escapeHtml(s.className)}</p>
        <table>
          <thead><tr><th>Subject</th><th>Marks</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>
        <p>Total: <strong>${s.totalMarksObtained} / ${s.totalMaxMarks}</strong>
           &nbsp; Percentage: <strong class="${cls}">${s.percentage}%</strong>
           &nbsp; Grade: <strong class="${cls}">${escapeHtml(s.grade)}</strong>
           &nbsp; ${resultText(s)}</p>
        ${reset}
      </div>`;
  }

  async function showResults(path) {
    clearMessage();
    results.innerHTML = "";
    try {
      const data = await callApi(path);
      const students = Array.isArray(data) ? data : [data];
      results.innerHTML = students.map(renderStudent).join("");
    } catch (e) {
      showMessage(e.message, "error");
    }
  }

  if (role === "student") {
    // Students don't search: they see their own result straight away.
    searchForm.hidden = true;
    document.querySelector("h1").textContent = "Your result";
    document.querySelector(".lead").textContent = "Your marks, percentage and grade.";
    results.before(document.getElementById("message")); // keep errors visible outside the hidden form
    showResults("/getResult");
  } else {
    searchForm.addEventListener("submit", (event) => {
      event.preventDefault();
      const by = document.getElementById("search-by").value;
      const value = document.getElementById("search-value").value.trim();
      showResults(`/getResult?${by}=${encodeURIComponent(value)}`);
    });

    results.addEventListener("click", async (event) => {
      const button = event.target.closest(".reset-password");
      if (!button) return;
      if (!confirm("Reset this student's password to their roll number?")) return;
      clearMessage();
      const body = new URLSearchParams();
      body.append("rollNo", button.dataset.roll);
      try {
        const data = await callApi("/resetPassword", { method: "POST", headers: FORM_HEADERS, body: body.toString() });
        showMessage(data.message, "ok");
      } catch (e) {
        showMessage(e.message, "error");
      }
    });
  }
}

/* ---------- Page 3: class summary ---------- */
const classForm = document.getElementById("class-form");
if (classForm) {
  const summary = document.getElementById("summary");
  let lastSummary = null;
  let chart = null;

  function drawChart(students) {
    if (typeof Chart === "undefined") return; // chart library didn't load (offline)
    if (chart) chart.destroy();
    chart = new Chart(document.getElementById("class-chart"), {
      type: "bar",
      data: {
        labels: students.map(s => s.name),
        datasets: [{
          label: "Percentage",
          data: students.map(s => s.percentage),
          backgroundColor: students.map(s => s.pass ? "#1f7a4d" : "#b3402f")
        }]
      },
      options: {
        maintainAspectRatio: false,
        scales: { y: { min: 0, max: 100, title: { display: true, text: "Percentage" } } },
        plugins: { legend: { display: false } }
      }
    });
  }

  function csvCell(value) {
    const text = String(value);
    return /[",\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
  }

  document.getElementById("download-report").addEventListener("click", () => {
    if (!lastSummary) return;
    const lines = [
      ["Roll no", "Name", "Class", "Total marks", "Max marks", "Percentage", "Grade", "Result"],
      ...lastSummary.students.map(s => [
        s.rollNo, s.name, s.className, s.totalMarksObtained, s.totalMaxMarks,
        s.percentage, s.grade, s.pass ? "Pass" : "Fail"
      ])
    ].map(row => row.map(csvCell).join(","));
    lines.push("");
    lines.push(["Class", lastSummary.className].map(csvCell).join(","));
    lines.push(["Students", lastSummary.studentCount].map(csvCell).join(","));
    lines.push(["Average percentage", lastSummary.averagePercentage].map(csvCell).join(","));
    lines.push(["Pass percentage", lastSummary.passPercentage].map(csvCell).join(","));
    lines.push(["Topper", lastSummary.topper.name].map(csvCell).join(","));

    const blob = new Blob([lines.join("\n")], { type: "text/csv" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = "class-" + lastSummary.className + "-report.csv";
    link.click();
    URL.revokeObjectURL(link.href);
  });

  classForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearMessage();
    summary.hidden = true;
    const className = document.getElementById("class-name").value.trim();
    try {
      const d = await callApi(`/classSummary?class=${encodeURIComponent(className)}`);
      document.getElementById("stat-count").textContent = d.studentCount;
      document.getElementById("stat-average").textContent = d.averagePercentage + "%";
      document.getElementById("stat-pass").textContent = d.passPercentage + "%";
      document.getElementById("stat-topper").textContent = `${d.topper.name} (${d.topper.percentage}%)`;
      document.getElementById("results-body").innerHTML = d.students.map(s => {
        const cls = s.pass ? "pass" : "fail";
        return `
        <tr class="row-${cls}">
          <td>${escapeHtml(s.rollNo)}</td>
          <td>${escapeHtml(s.name)}</td>
          <td>${s.totalMarksObtained} / ${s.totalMaxMarks}</td>
          <td class="${cls}">${s.percentage}%</td>
          <td class="${cls}">${escapeHtml(s.grade)}</td>
          <td>${resultText(s)}</td>
        </tr>`;
      }).join("");
      summary.hidden = false;
      lastSummary = d;
      drawChart(d.students);
    } catch (e) {
      showMessage(e.message, "error");
    }
  });
}

/* ---------- Import page: upload an Excel / CSV sheet ---------- */
const importSection = document.getElementById("import-section");
if (importSection) {
  const fileInput = document.getElementById("file-input");
  const preview = document.getElementById("preview");
  const importBtn = document.getElementById("import-btn");
  const report = document.getElementById("report");
  let parsed = [];

  function cellText(row, index) {
    const value = row[index];
    return value === undefined || value === null ? "" : String(value).trim();
  }

  function findColumn(headers, names) {
    return headers.findIndex(h => names.includes(String(h).trim().toLowerCase().replace(/[\s._]/g, "")));
  }

  // Turns the sheet (an array of rows) into a list of students, flagging rows with problems.
  function parseSheet(rows) {
    if (rows.length < 2) throw new Error("The sheet has no student rows.");
    const headers = rows[0];
    const rollCol = findColumn(headers, ["rollno", "roll", "rollnumber"]);
    const nameCol = findColumn(headers, ["name", "studentname", "fullname"]);
    const classCol = findColumn(headers, ["class", "classname", "section"]);
    if (rollCol < 0 || nameCol < 0 || classCol < 0) {
      throw new Error("The first row must have the columns Roll No, Name and Class.");
    }

    // Every other column is a subject. A header like "Chemistry (50)" means out of 50; otherwise 100.
    const subjectCols = [];
    headers.forEach((h, index) => {
      const text = String(h).trim();
      if (!text || index === rollCol || index === nameCol || index === classCol) return;
      const match = text.match(/^(.*?)\s*\((\d+(?:\.\d+)?)\)$/);
      subjectCols.push({ index, name: match ? match[1] : text, max: match ? Number(match[2]) : 100 });
    });

    const students = [];
    for (let r = 1; r < rows.length; r++) {
      const row = rows[r];
      if (row.every((_, i) => cellText(row, i) === "")) continue; // skip blank rows
      const student = {
        row: r + 1,
        rollNo: cellText(row, rollCol),
        name: cellText(row, nameCol),
        className: cellText(row, classCol),
        subjects: [],
        error: ""
      };
      if (!student.rollNo || !student.name || !student.className) {
        student.error = "Roll no, name and class are all required.";
      }
      for (const col of subjectCols) {
        const cell = cellText(row, col.index);
        if (cell === "") continue; // no mark for this subject
        const marks = Number(cell);
        if (!Number.isFinite(marks) || marks < 0 || marks > col.max) {
          student.error = student.error || `${col.name}: "${cell}" is not a valid mark (0 to ${col.max}).`;
        } else {
          student.subjects.push({ name: col.name.replace(/[,:;]/g, " ").trim(), marks, max: col.max });
        }
      }
      students.push(student);
    }
    return students;
  }

  function renderPreview() {
    const ready = parsed.filter(s => !s.error).length;
    const problems = parsed.length - ready;
    const rows = parsed.map(s => `
      <tr class="${s.error ? "row-fail" : ""}">
        <td>${s.row}</td>
        <td>${escapeHtml(s.rollNo)}</td>
        <td>${escapeHtml(s.name)}</td>
        <td>${escapeHtml(s.className)}</td>
        <td>${s.subjects.map(x => escapeHtml(x.name) + " " + x.marks + "/" + x.max).join(", ")}</td>
        <td>${s.error ? '<span class="fail">' + escapeHtml(s.error) + "</span>" : '<span class="pass">Ready</span>'}</td>
      </tr>`).join("");
    preview.innerHTML = `
      <div class="card">
        <h2>Preview</h2>
        <p class="lead">${parsed.length} students found: ${ready} ready${problems ? `, ${problems} with problems (these will be skipped)` : ""}.</p>
        <div class="table-scroll">
          <table>
            <thead><tr><th>Row</th><th>Roll no.</th><th>Name</th><th>Class</th><th>Marks</th><th>Status</th></tr></thead>
            <tbody>${rows}</tbody>
          </table>
        </div>
      </div>`;
    importBtn.textContent = `Import ${ready} student${ready === 1 ? "" : "s"}`;
    importBtn.hidden = ready === 0;
  }

  document.getElementById("download-template").addEventListener("click", () => {
    if (typeof XLSX === "undefined") {
      showMessage("The spreadsheet library didn't load. Check your internet connection.", "error");
      return;
    }
    const sheet = XLSX.utils.aoa_to_sheet([
      ["Roll No", "Name", "Class", "Maths", "Physics", "Chemistry (50)"],
      ["201", "Aarav Sharma", "10B", 88, 76, 41],
      ["202", "Meera Iyer", "10B", 92, 81, 45]
    ]);
    const book = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(book, sheet, "Students");
    XLSX.writeFile(book, "student-import-template.xlsx");
  });

  fileInput.addEventListener("change", () => {
    clearMessage();
    report.innerHTML = "";
    preview.innerHTML = "";
    importBtn.hidden = true;
    parsed = [];
    const file = fileInput.files[0];
    if (!file) return;
    if (typeof XLSX === "undefined") {
      showMessage("The spreadsheet library didn't load. Check your internet connection.", "error");
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const book = XLSX.read(reader.result, { type: "array" });
        const rows = XLSX.utils.sheet_to_json(book.Sheets[book.SheetNames[0]], { header: 1, defval: "" });
        parsed = parseSheet(rows);
        renderPreview();
      } catch (e) {
        showMessage(e.message || "Couldn't read that file. Use an .xlsx, .xls or .csv file in the template format.", "error");
      }
    };
    reader.readAsArrayBuffer(file);
  });

  importBtn.addEventListener("click", async () => {
    const valid = parsed.filter(s => !s.error);
    const body = new URLSearchParams();
    valid.forEach(s => {
      body.append("row", s.row);
      body.append("rollNo", s.rollNo);
      body.append("name", s.name);
      body.append("className", s.className);
      body.append("subjects", s.subjects.map(x => `${x.name}:${x.marks}:${x.max}`).join(";"));
    });
    clearMessage();
    importBtn.disabled = true;
    try {
      const data = await callApi("/importStudents", { method: "POST", headers: FORM_HEADERS, body: body.toString() });
      const problems = parsed.filter(s => s.error)
        .map(s => ({ row: s.row, rollNo: s.rollNo, reason: s.error }))
        .concat(data.skipped);
      const problemRows = problems.map(p =>
        `<tr><td>${escapeHtml(p.row)}</td><td>${escapeHtml(p.rollNo)}</td><td>${escapeHtml(p.reason)}</td></tr>`).join("");
      report.innerHTML = `
        <div class="card">
          <h2>Import finished</h2>
          <p><strong>${data.added}</strong> student${data.added === 1 ? "" : "s"} added, <strong>${problems.length}</strong> skipped.
             New students log in with their roll number as both username and first password.</p>
          ${problems.length ? `<table><thead><tr><th>Row</th><th>Roll no.</th><th>Reason skipped</th></tr></thead><tbody>${problemRows}</tbody></table>` : ""}
        </div>`;
      preview.innerHTML = "";
      importBtn.hidden = true;
      parsed = [];
      fileInput.value = "";
    } catch (e) {
      showMessage(e.message, "error");
    } finally {
      importBtn.disabled = false;
    }
  });
}
