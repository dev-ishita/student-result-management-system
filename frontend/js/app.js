// Shared script for all three pages. Each block only runs if its page's elements exist.
const API = "http://localhost:8080";

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = String(text);
  return div.innerHTML;
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

// Calls the backend and returns the parsed JSON. Throws an Error with a readable message on failure.
async function callApi(path, options) {
  let response;
  try {
    response = await fetch(API + path, options);
  } catch (e) {
    throw new Error("Can't reach the server. Start it with: java com.srms.server.Server");
  }
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "Something went wrong.");
  return data;
}

function resultText(student) {
  return student.pass
    ? '<span class="pass">Pass</span>'
    : '<span class="fail">Fail</span>';
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
      const s = await callApi("/addStudent", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: body.toString()
      });
      showMessage(`Saved ${s.name} (roll ${s.rollNo}): ${s.percentage}%, grade ${s.grade}, ${s.pass ? "pass" : "fail"}.`, "ok");
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
    const rows = s.subjects.map(sub =>
      `<tr><td>${escapeHtml(sub.name)}</td><td>${sub.marksObtained} / ${sub.maxMarks}</td></tr>`
    ).join("");
    return `
      <div class="card">
        <h2>${escapeHtml(s.name)}</h2>
        <p class="lead">Roll ${escapeHtml(s.rollNo)} &middot; Class ${escapeHtml(s.className)}</p>
        <table>
          <thead><tr><th>Subject</th><th>Marks</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>
        <p>Total: <strong>${s.totalMarksObtained} / ${s.totalMaxMarks}</strong>
           &nbsp; Percentage: <strong>${s.percentage}%</strong>
           &nbsp; Grade: <strong>${escapeHtml(s.grade)}</strong>
           &nbsp; ${resultText(s)}</p>
      </div>`;
  }

  searchForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearMessage();
    results.innerHTML = "";
    const by = document.getElementById("search-by").value;
    const value = document.getElementById("search-value").value.trim();
    try {
      const data = await callApi(`/getResult?${by}=${encodeURIComponent(value)}`);
      const students = Array.isArray(data) ? data : [data];
      results.innerHTML = students.map(renderStudent).join("");
    } catch (e) {
      showMessage(e.message, "error");
    }
  });
}

/* ---------- Page 3: class summary ---------- */
const classForm = document.getElementById("class-form");
if (classForm) {
  const summary = document.getElementById("summary");

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
      document.getElementById("results-body").innerHTML = d.students.map(s => `
        <tr>
          <td>${escapeHtml(s.rollNo)}</td>
          <td>${escapeHtml(s.name)}</td>
          <td>${s.totalMarksObtained} / ${s.totalMaxMarks}</td>
          <td>${s.percentage}%</td>
          <td>${escapeHtml(s.grade)}</td>
          <td>${resultText(s)}</td>
        </tr>`).join("");
      summary.hidden = false;
    } catch (e) {
      showMessage(e.message, "error");
    }
  });
}
