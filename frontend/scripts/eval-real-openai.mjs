import fs from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright-core";

const backendUrl = process.env.EVAL_BACKEND_URL ?? "http://127.0.0.1:8080";
const frontendUrl = process.env.EVAL_FRONTEND_URL ?? "http://127.0.0.1:3000";
const requestedCases = (process.env.EVAL_CASES ?? "")
  .split(",")
  .map((value) => value.trim())
  .filter(Boolean);
const evalSuite = process.env.EVAL_SUITE ?? "base";
const scriptDir = fileURLToPath(new URL(".", import.meta.url));
const browserCandidates = [
  process.env.EVAL_BROWSER,
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
].filter(Boolean);
const resumeMode = process.env.EVAL_RESUME === "1" || process.env.EVAL_RESUME === "true";
const checkpointPath = path.resolve(scriptDir, "eval-real-openai-journey-checkpoint.json");
const runTag = resumeMode
  ? await (async () => {
      try { return JSON.parse(await fs.readFile(checkpointPath, "utf8")).runTag; } catch { return null; }
    })() ?? new Date().toISOString().replace(/\D/g, "").slice(0, 14)
  : (process.env.EVAL_RUN_TAG ?? new Date().toISOString().replace(/\D/g, "").slice(0, 14));
const journeyStepCount = integerEnv(process.env.EVAL_JOURNEY_STEPS, 540);
const includeDebugTrace = booleanEnv(process.env.EVAL_INCLUDE_DEBUG_TRACE, evalSuite === "journey");
const checkpointEvery = integerEnv(process.env.EVAL_JOURNEY_CHECKPOINT_EVERY, evalSuite === "journey" ? 25 : 0);
const maxEstimatedCostUsd = decimalEnv(process.env.EVAL_MAX_ESTIMATED_COST_USD, null);
const maxTotalTokens = integerEnv(process.env.EVAL_MAX_TOTAL_TOKENS, null);
const maxConsecutiveFailures = integerEnv(process.env.EVAL_STOP_AFTER_CONSECUTIVE_FAILURES, null);
const rollingWindowSize = integerEnv(process.env.EVAL_JOURNEY_ROLLING_WINDOW, 25);

function fail(message) {
  throw new Error(message);
}

function integerEnv(rawValue, defaultValue) {
  if (rawValue == null || rawValue === "") {
    return defaultValue;
  }
  const parsed = Number.parseInt(rawValue, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : defaultValue;
}

function decimalEnv(rawValue, defaultValue) {
  if (rawValue == null || rawValue === "") {
    return defaultValue;
  }
  const parsed = Number(rawValue);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : defaultValue;
}

function booleanEnv(rawValue, defaultValue) {
  if (rawValue == null || rawValue === "") {
    return defaultValue;
  }
  return !/^(0|false|no|off)$/i.test(String(rawValue));
}

async function request(pathname, init) {
  const response = await fetch(`${backendUrl}${pathname}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    }
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || `${init?.method ?? "GET"} ${pathname} failed with ${response.status}`);
  }
  return text ? JSON.parse(text) : null;
}

function browserPath() {
  for (const candidate of browserCandidates) {
    if (candidate) {
      return candidate;
    }
  }
  fail("No local Chrome or Edge executable found.");
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function scopedTitle(baseTitle) {
  return `${baseTitle} ${runTag}`;
}

function genericEditorJson(title, body, summary = "") {
  return JSON.stringify([
    heading(1, title),
    heading(2, "Summary"),
    paragraph(summary),
    heading(2, "Body"),
    paragraph(body),
    heading(2, "Action Items"),
    paragraph(""),
    heading(2, "References"),
    paragraph(""),
    heading(2, "Inbox"),
    paragraph("")
  ]);
}

function projectEditorJson(title, values) {
  return JSON.stringify([
    heading(1, title),
    heading(2, "Summary"),
    paragraph(values.summary ?? ""),
    heading(2, "Status"),
    paragraph(values.status ?? ""),
    heading(2, "Decisions"),
    paragraph(values.decisions ?? ""),
    heading(2, "Tasks"),
    paragraph(values.tasks ?? ""),
    heading(2, "Open Questions"),
    paragraph(values.openQuestions ?? ""),
    heading(2, "Timeline"),
    paragraph(values.timeline ?? ""),
    heading(2, "References"),
    paragraph(values.references ?? ""),
    heading(2, "Inbox"),
    paragraph("")
  ]);
}

function heading(level, text) {
  return {
    type: "heading",
    props: { level },
    content: [{ type: "text", text, styles: {} }],
    children: []
  };
}

function paragraph(text) {
  return {
    type: "paragraph",
    content: text
      ? [{ type: "text", text, styles: {} }]
      : [],
    children: []
  };
}

async function seedFixtures() {
  const notebooks = await request("/api/v2/notebooks");
  const notebookByName = Object.fromEntries(notebooks.map((notebook) => [notebook.name, notebook]));
  const titles = {
    workLaunch: scopedTitle("NotesZero Launch"),
    websiteOps: scopedTitle("Website Operations"),
    apiIntegrations: scopedTitle("API Integrations"),
    workRunbook: scopedTitle("Deploy Runbook"),
    workGrowthPlan: scopedTitle("Growth Experiment Plan"),
    workEnvVars: scopedTitle("Railway Env Vars"),
    workLatency: scopedTitle("Latency Tracking"),
    dogVetLog: scopedTitle("Dog Vet Log"),
    dogTraining: scopedTitle("Dog Training Plan"),
    dogGear: scopedTitle("Dog Gear Wishlist"),
    dogMedicationLog: scopedTitle("Dog Medication Log"),
    dogStormAnxiety: scopedTitle("Storm Anxiety Observations"),
    feverTracking: scopedTitle("Fever Tracking"),
    pediatrician: scopedTitle("Pediatrician Follow-up"),
    familySleepLog: scopedTitle("Sleep Notes"),
    familyAllergy: scopedTitle("Allergy Observations"),
    familyEnt: scopedTitle("ENT Follow-up"),
    promptEvalIdeas: scopedTitle("Prompt Eval Ideas"),
    boardingChecklist: scopedTitle("Boarding Checklist"),
    sleepRegression: scopedTitle("Sleep Regression Observations"),
    sequenceProject: scopedTitle("Extended Eval Stream")
  };

  const created = {};
  created.workLaunch = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Work websites"].id,
      noteType: "project_note/v1",
      editorContent: projectEditorJson(titles.workLaunch, {
        summary: "Launch tracker for the AI-native notes product.",
        status: "V2 write flow is deployed locally.",
        decisions: "- Use mock AI by default in tests.",
        tasks: "- [ ] Add real OpenAI evals",
        openQuestions: "- When should the planner move to a larger model?",
        timeline: "- 2026-03-10 Started live OpenAI validation.",
        references: "- https://platform.openai.com/docs"
      })
    })
  });
  created.websiteOps = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Work websites"].id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(
        titles.websiteOps,
        "Hosting, DNS, SSL, deploy links, vendor docs, and runbooks.",
        "Operational links and website maintenance notes."
      )
    })
  });
  created.apiIntegrations = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Work websites"].id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(
        titles.apiIntegrations,
        "OpenAI and third-party integration notes. Current next step: refine prompts.",
        "Integration notes for external APIs."
      )
    })
  });
  created.workRunbook = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Work websites"].id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(
        titles.workRunbook,
        "Deploy order: backend build, migrations, frontend release. Rollback uses the previous green build.",
        "Deployment and rollback steps for NotesZero."
      )
    })
  });
  created.workGrowthPlan = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Work websites"].id,
      noteType: "project_note/v1",
      editorContent: projectEditorJson(titles.workGrowthPlan, {
        summary: "Experiment plan for onboarding and activation improvements.",
        status: "Preparing the first onboarding cohort.",
        decisions: "- Focus on activation within the first session.",
        tasks: "- [ ] Draft experiment brief",
        openQuestions: "- Which activation metric should gate rollout?",
        timeline: "- 2026-03-10 Defined the initial hypothesis.",
        references: "- Internal onboarding notes."
      })
    })
  });
  created.dogVetLog = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Dog notes"].id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(
        titles.dogVetLog,
        "Track symptoms, appetite, stool, meds, and follow-up questions for Uma.",
        "Health observations and vet follow-ups for the dog."
      )
    })
  });
  created.dogMedicationLog = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Dog notes"].id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(
        titles.dogMedicationLog,
        "Track doses, times, reactions, and missed doses for Uma.",
        "Medication timing and response log."
      )
    })
  });
  created.dogTraining = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Dog notes"].id,
      noteType: "project_note/v1",
      editorContent: projectEditorJson(titles.dogTraining, {
        summary: "Training plan for calm greetings and leash manners.",
        status: "Making progress with shorter sessions.",
        decisions: "- Keep sessions under 10 minutes.",
        tasks: "- [ ] Practice loose-leash walking",
        openQuestions: "- How should we reward calm greetings?",
        timeline: "- 2026-03-09 Began shorter daily sessions.",
        references: "- Trainer notes from last week."
      })
    })
  });
  created.dogGear = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Dog notes"].id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(
        titles.dogGear,
        "Gear ideas for walking, travel, and rain.",
        "Links and notes for dog gear to buy later."
      )
    })
  });
  created.feverTracking = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Family health"].id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(
        titles.feverTracking,
        "Track temperature readings, energy, cough, and medicine timing.",
        "Temperature log and symptom tracking."
      )
    })
  });
  created.pediatrician = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Family health"].id,
      noteType: "project_note/v1",
      editorContent: projectEditorJson(titles.pediatrician, {
        summary: "Questions and follow-ups for the next pediatrician conversation.",
        status: "Collecting questions before the appointment.",
        decisions: "",
        tasks: "- [ ] Bring symptom timeline",
        openQuestions: "- Should we adjust nap timing during recovery?",
        timeline: "",
        references: ""
      })
    })
  });
  created.familySleepLog = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Family health"].id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(
        titles.familySleepLog,
        "Track bedtime, wakeups, naps, and suspected triggers. Recent pattern includes early wakeups around 6am.",
        "Sleep observations and routine notes."
      )
    })
  });
  created.sequenceProject = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: notebookByName["Work websites"].id,
      noteType: "project_note/v1",
      editorContent: projectEditorJson(titles.sequenceProject, {
        summary: "Working note for iterative AI write testing.",
        status: "Starting the sequence run.",
        decisions: "- Keep the note structured by section.",
        tasks: "- [ ] Seed the initial note",
        openQuestions: "- Will repeated writes stay tidy?",
        timeline: "- 2026-03-10 Seeded the iterative test note.",
        references: "- https://platform.openai.com/docs/guides/structured-outputs"
      })
    })
  });

  // Trigger index backfill so freshly-created notes are retrievable immediately
  await request("/api/v2/index/backfill", { method: "POST" });
  await new Promise((resolve) => setTimeout(resolve, 1500));

  const fixtures = { notebookByName, notes: created, titles };

  // Save checkpoint so the run can be resumed if it crashes
  await fs.writeFile(checkpointPath, JSON.stringify({ runTag, fixtures }, null, 2));
  console.error(`[checkpoint] saved to ${checkpointPath}`);

  return fixtures;
}

async function seedOrLoadFixtures() {
  if (!resumeMode) return seedFixtures();
  try {
    const saved = JSON.parse(await fs.readFile(checkpointPath, "utf8"));
    if (saved.runTag !== runTag) {
      console.error(`[checkpoint] runTag mismatch (saved=${saved.runTag}, current=${runTag}), re-seeding`);
      return seedFixtures();
    }
    console.error(`[checkpoint] resuming run ${runTag}, skipping seed`);
    return saved.fixtures;
  } catch {
    console.error("[checkpoint] not found, seeding fresh");
    return seedFixtures();
  }
}

function baseCases(fixtures) {
  const workId = fixtures.notebookByName["Work websites"].id;
  const dogId = fixtures.notebookByName["Dog notes"].id;
  const familyId = fixtures.notebookByName["Family health"].id;

  return [
    {
      name: "selected project task",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: "Add a task to this note to compare GPT-5 mini and GPT-5.4 latency next week.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workLaunch.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "tasks",
        format: "checklist",
        contains: ["GPT-5 mini", "GPT-5.4"]
      }
    },
    {
      name: "selected project decision",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: "We decided to keep mock AI as the default for tests and smoke runs.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workLaunch.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "decisions",
        format: "bullet",
        contains: ["mock AI", "tests"]
      }
    },
    {
      name: "selected project status update",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: "Status update: local OpenAI endpoint is now working in development.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workLaunch.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "status",
        format: "prose",
        contains: ["OpenAI endpoint", "development"]
      }
    },
    {
      name: "selected project timeline",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: "Add a timeline event for 2026-03-10: real OpenAI wiring validated locally.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workLaunch.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "timeline",
        format: "bullet",
        contains: ["2026-03-10", "validated locally"]
      }
    },
    {
      name: "selected project open question",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: "Open question: should we move the planner to GPT-5.4 after evals?",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workLaunch.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "open_questions",
        format: "bullet",
        contains: ["GPT-5.4", "evals"]
      }
    },
    {
      name: "selected generic references",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.websiteOps.id,
      waitForText: fixtures.notes.websiteOps.title,
      message: "Save this docs link: https://platform.openai.com/docs/guides/structured-outputs",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.websiteOps.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "references",
        format: "bullet",
        contains: ["structured-outputs", "https://platform.openai.com/docs/guides/structured-outputs"]
      }
    },
    {
      name: "create project note in selected work notebook",
      notebookName: "Work websites",
      message: `Create a project note called ${fixtures.titles.promptEvalIdeas}. Add a summary that we should measure wrong-target writes and a task to build a curated eval set.`,
      expect: {
        intent: "CREATE_NOTE",
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        noteType: "project_note/v1",
        sectionId: "tasks",
        format: "checklist",
        contains: ["curated eval set"]
      }
    },
    {
      name: "selected generic rewrite",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.apiIntegrations.id,
      waitForText: fixtures.notes.apiIntegrations.title,
      message: "Rewrite this note into a cleaner summary with next steps. Preserve that it is about OpenAI and third-party integrations.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.apiIntegrations.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        opAnyOf: ["REPLACE_NOTE_OUTLINE", "REPLACE_SECTION_CONTENT"],
        visibleContainsAny: ["OpenAI", "third-party", "next steps"]
      }
    },
    {
      name: "selected dog symptom note",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogVetLog.id,
      waitForText: fixtures.notes.dogVetLog.title,
      message: "My dog threw up once after breakfast but is acting normal otherwise.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogVetLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "body",
        format: "prose",
        contains: ["acting normal"],
        containsAny: ["threw up", "vomiting"],
        visibleContainsAny: ["threw up", "vomiting"]
      }
    },
    {
      name: "selected dog vet action item",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogVetLog.id,
      waitForText: fixtures.notes.dogVetLog.title,
      message: "Add an action item to ask the vet about travel meds before our trip.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogVetLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "action_items",
        format: "checklist",
        contains: ["travel meds", "vet"]
      }
    },
    {
      name: "selected dog training task",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogTraining.id,
      waitForText: fixtures.notes.dogTraining.title,
      message: "Add a task to practice loose-leash walking for 10 minutes daily.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogTraining.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "tasks",
        format: "checklist",
        contains: ["loose-leash", "10 minutes"]
      }
    },
    {
      name: "selected dog training decision",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogTraining.id,
      waitForText: fixtures.notes.dogTraining.title,
      message: "We agreed to stop using the retractable leash.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogTraining.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "decisions",
        format: "bullet",
        contains: ["retractable leash"]
      }
    },
    {
      name: "create dog boarding note",
      notebookName: "Dog notes",
      message: `Create a project note called ${fixtures.titles.boardingChecklist} with food, meds, and emergency contacts. Add a task to pack food.`,
      expect: {
        intent: "CREATE_NOTE",
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        noteType: "project_note/v1",
        sectionId: "tasks",
        format: "checklist",
        contains: ["pack food"]
      }
    },
    {
      name: "selected fever update",
      notebookName: "Family health",
      selectNoteId: fixtures.notes.feverTracking.id,
      waitForText: fixtures.notes.feverTracking.title,
      message: "Update this note: the fever peaked at 38.7C around 7pm today.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.feverTracking.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "body",
        format: "prose",
        contains: ["38.7"],
        containsAny: ["7pm", "7:00 PM"],
        visibleContainsAny: ["7pm", "7:00 PM"]
      }
    },
    {
      name: "selected pediatrician open question",
      notebookName: "Family health",
      selectNoteId: fixtures.notes.pediatrician.id,
      waitForText: fixtures.notes.pediatrician.title,
      message: "Add a question: when should we worry if the cough lasts more than 10 days?",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.pediatrician.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "open_questions",
        format: "bullet",
        contains: ["cough", "10 days"]
      }
    },
    {
      name: "create family note",
      notebookName: "Family health",
      message: `Create a note called ${fixtures.titles.sleepRegression}. Add a summary and mention early waking plus shorter naps in the body.`,
      expect: {
        intent: "CREATE_NOTE",
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "body",
        format: "prose",
        contains: ["shorter naps"],
        containsAny: ["early waking", "early morning waking"],
        visibleContainsAny: ["early waking", "shorter naps", "early morning waking"]
      }
    },
    {
      name: "answer from selected launch note",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: "What does this note say about status?",
      expect: {
        intent: "ANSWER_ONLY",
        strategy: "ANSWER_ONLY",
        notebookId: workId,
        answerContains: ["openai endpoint", "development"]
      }
    },
    {
      name: "cross-route existing launch note",
      notebookName: "Work websites",
      message: `In ${fixtures.notes.workLaunch.title}, add a task to prepare the rollout checklist.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workLaunch.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "tasks",
        format: "checklist",
        contains: ["rollout checklist"]
      }
    },
    {
      name: "cross-route dog gear link",
      notebookName: "Dog notes",
      message: `In ${fixtures.notes.dogGear.title}, save this link: https://ruffwear.com`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogGear.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "references",
        format: "bullet",
        contains: ["ruffwear.com"]
      }
    },
    {
      name: "ambiguous capture fallback",
      notebookName: "Work websites",
      message: "Need to remember this at some point.",
      expect: {
        intent: "CREATE_NOTE",
        strategy: "NOTEBOOK_INBOX",
        notebookId: workId,
        fallbackToInbox: true
      }
    }
  ];
}

function extendedCases(fixtures) {
  const workId = fixtures.notebookByName["Work websites"].id;
  const dogId = fixtures.notebookByName["Dog notes"].id;
  const familyId = fixtures.notebookByName["Family health"].id;

  return [
    {
      name: "selected runbook command preserve",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workRunbook.id,
      waitForText: fixtures.notes.workRunbook.title,
      message: "Add the deploy command `railway up --detach` to the body of this note.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workRunbook.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "body",
        format: "prose",
        contains: ["railway up --detach"]
      }
    },
    {
      name: "selected runbook bullet list requested",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workRunbook.id,
      waitForText: fixtures.notes.workRunbook.title,
      message: "In the body, add a bullet list for deploy order: backend, migrations, frontend.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workRunbook.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "body",
        format: "bullet",
        contains: ["backend", "migrations", "frontend"]
      }
    },
    {
      name: "cross-route runbook from launch",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: `In ${fixtures.notes.workRunbook.title}, add a rollback note to redeploy the previous green build.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workRunbook.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "body",
        format: "prose",
        contains: ["rollback", "previous green build"]
      }
    },
    {
      name: "answer from runbook while launch selected",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: `What does ${fixtures.notes.workRunbook.title} say about rollback?`,
      expect: {
        intent: "ANSWER_ONLY",
        strategy: "ANSWER_ONLY",
        notebookId: workId,
        targetNoteId: fixtures.notes.workRunbook.id,
        answerContains: ["rollback", "green build"]
      }
    },
    {
      name: "create work env vars note",
      notebookName: "Work websites",
      message: `Create a note called ${fixtures.titles.workEnvVars}. Add a summary and mention RAILWAY_TOKEN and OPENAI_API_KEY in the body.`,
      expect: {
        intent: "CREATE_NOTE",
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        noteType: "generic_note/v1",
        sectionId: "body",
        format: "prose",
        contains: ["RAILWAY_TOKEN", "OPENAI_API_KEY"]
      }
    },
    {
      name: "selected website ops multiple urls",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.websiteOps.id,
      waitForText: fixtures.notes.websiteOps.title,
      message: "Add these references: https://railway.com/docs and https://flywaydb.org/documentation/",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.websiteOps.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "references",
        format: "bullet",
        contains: ["railway.com/docs", "flywaydb.org/documentation"]
      }
    },
    {
      name: "selected api integrations model action item",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.apiIntegrations.id,
      waitForText: fixtures.notes.apiIntegrations.title,
      message: "Add an action item to compare gpt-5-mini-2025-08-07 and gpt-5.4-2026-03-05 in evals.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.apiIntegrations.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "action_items",
        format: "checklist",
        contains: ["gpt-5-mini-2025-08-07", "gpt-5.4-2026-03-05"]
      }
    },
    {
      name: "create work latency project note",
      notebookName: "Work websites",
      message: `Create a project note called ${fixtures.titles.workLatency}. Add a summary about latency tracking and an open question about streaming responses.`,
      expect: {
        intent: "CREATE_NOTE",
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        noteType: "project_note/v1",
        sectionId: "open_questions",
        format: "bullet",
        contains: ["streaming responses"]
      }
    },
    {
      name: "selected growth plan timeline",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workGrowthPlan.id,
      waitForText: fixtures.notes.workGrowthPlan.title,
      message: "Add a timeline event for 2026-03-12: ran the first onboarding experiment.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workGrowthPlan.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "timeline",
        format: "bullet",
        contains: ["2026-03-12", "onboarding experiment"]
      }
    },
    {
      name: "selected growth plan replace status",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workGrowthPlan.id,
      waitForText: fixtures.notes.workGrowthPlan.title,
      message: "Replace the status with: awaiting experiment results from the first cohort.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workGrowthPlan.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "status",
        format: "prose",
        contains: ["awaiting experiment results", "first cohort"]
      }
    },
    {
      name: "ambiguous work reminder hidden",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workRunbook.id,
      waitForText: fixtures.notes.workRunbook.title,
      message: "Remember this for later.",
      expect: {
        intent: "CREATE_NOTE",
        strategy: "NOTEBOOK_INBOX",
        notebookId: workId,
        fallbackToInbox: true
      }
    },
    {
      name: "selected dog med exact dosage",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogMedicationLog.id,
      waitForText: fixtures.notes.dogMedicationLog.title,
      message: "Add to the body: gabapentin 5 mg at 8:15pm before fireworks.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogMedicationLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "body",
        format: "prose",
        contains: ["gabapentin", "5 mg", "8:15pm"]
      }
    },
    {
      name: "selected dog med refill action",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogMedicationLog.id,
      waitForText: fixtures.notes.dogMedicationLog.title,
      message: "Add an action item to refill trazodone by Friday.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogMedicationLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "action_items",
        format: "checklist",
        contains: ["refill trazodone", "Friday"]
      }
    },
    {
      name: "cross-route dog med from gear",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogGear.id,
      waitForText: fixtures.notes.dogGear.title,
      message: `In ${fixtures.notes.dogMedicationLog.title}, note that we skipped the morning dose because breakfast was late.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogMedicationLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "body",
        format: "prose",
        contains: ["skipped the morning dose", "breakfast was late"]
      }
    },
    {
      name: "selected dog training open question threshold",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogTraining.id,
      waitForText: fixtures.notes.dogTraining.title,
      message: "Add an open question about whether the threshold distance is too short around other dogs.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogTraining.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "open_questions",
        format: "bullet",
        contains: ["threshold distance", "other dogs"]
      }
    },
    {
      name: "selected dog training status outdoors",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogTraining.id,
      waitForText: fixtures.notes.dogTraining.title,
      message: "Status update: calm greetings are improving indoors but still inconsistent outside.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogTraining.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "status",
        format: "prose",
        contains: ["indoors", "outside"]
      }
    },
    {
      name: "selected dog gear multiple references",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogGear.id,
      waitForText: fixtures.notes.dogGear.title,
      message: "Add these references: https://ruffwear.com and https://kurgo.com",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogGear.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "references",
        format: "bullet",
        contains: ["ruffwear.com", "kurgo.com"]
      }
    },
    {
      name: "create dog storm note",
      notebookName: "Dog notes",
      message: `Create a note called ${fixtures.titles.dogStormAnxiety}. Add a summary and mention shaking, pacing, and hiding during storms in the body.`,
      expect: {
        intent: "CREATE_NOTE",
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        noteType: "generic_note/v1",
        sectionId: "body",
        format: "prose",
        contains: ["shaking", "pacing", "hiding"]
      }
    },
    {
      name: "answer dog training status from gear",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogGear.id,
      waitForText: fixtures.notes.dogGear.title,
      message: `What does ${fixtures.notes.dogTraining.title} say about status?`,
      expect: {
        intent: "ANSWER_ONLY",
        strategy: "ANSWER_ONLY",
        notebookId: dogId,
        targetNoteId: fixtures.notes.dogTraining.id,
        answerContainsAny: ["shorter sessions", "indoors", "outdoors", "outside"]
      }
    },
    {
      name: "ambiguous dog reminder hidden",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogVetLog.id,
      waitForText: fixtures.notes.dogVetLog.title,
      message: "Need to remember this before the weekend.",
      expect: {
        intent: "CREATE_NOTE",
        strategy: "NOTEBOOK_INBOX",
        notebookId: dogId,
        fallbackToInbox: true
      }
    },
    {
      name: "selected dog vet quoted phrase",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogVetLog.id,
      waitForText: fixtures.notes.dogVetLog.title,
      message: "Add this observation to the body exactly: \"wouldn't settle for 30 minutes after the thunder\".",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogVetLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "body",
        format: "prose",
        contains: ["wouldn't settle for 30 minutes after the thunder"]
      }
    },
    {
      name: "cross-notebook dog from family selected",
      notebookName: "Family health",
      selectNoteId: fixtures.notes.feverTracking.id,
      waitForText: fixtures.notes.feverTracking.title,
      message: `In ${fixtures.notes.dogVetLog.title}, add that appetite was normal again by dinner.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogVetLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "body",
        format: "prose",
        contains: ["appetite was normal again", "dinner"]
      }
    },
    {
      name: "selected fever exact medicine timing",
      notebookName: "Family health",
      selectNoteId: fixtures.notes.feverTracking.id,
      waitForText: fixtures.notes.feverTracking.title,
      message: "Add to the body: ibuprofen 5 mL at 19:15 and temperature 37.9C thirty minutes later.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.feverTracking.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "body",
        format: "prose",
        contains: ["ibuprofen", "5 mL", "19:15", "37.9C"]
      }
    },
    {
      name: "selected pediatrician task video",
      notebookName: "Family health",
      selectNoteId: fixtures.notes.pediatrician.id,
      waitForText: fixtures.notes.pediatrician.title,
      message: "Add a task to bring a short video of the cough to the appointment.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.pediatrician.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "tasks",
        format: "checklist",
        contains: ["video of the cough", "appointment"]
      }
    },
    {
      name: "cross-route family sleep from pediatrician",
      notebookName: "Family health",
      selectNoteId: fixtures.notes.pediatrician.id,
      waitForText: fixtures.notes.pediatrician.title,
      message: `In ${fixtures.notes.familySleepLog.title}, add that bedtime moved to 8:40pm and wakeup was 5:55am.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.familySleepLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "body",
        format: "prose",
        contains: ["8:40pm", "5:55am"]
      }
    },
    {
      name: "answer family sleep from fever",
      notebookName: "Family health",
      selectNoteId: fixtures.notes.feverTracking.id,
      waitForText: fixtures.notes.feverTracking.title,
      message: `What does ${fixtures.notes.familySleepLog.title} say about wakeups?`,
      expect: {
        intent: "ANSWER_ONLY",
        strategy: "ANSWER_ONLY",
        notebookId: familyId,
        targetNoteId: fixtures.notes.familySleepLog.id,
        answerContains: ["6", "wake"]
      }
    },
    {
      name: "create family allergy note",
      notebookName: "Family health",
      message: `Create a note called ${fixtures.titles.familyAllergy}. Add a summary and mention itchy eyes, sneezing, and congestion in the body.`,
      expect: {
        intent: "CREATE_NOTE",
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        noteType: "generic_note/v1",
        sectionId: "body",
        format: "prose",
        contains: ["itchy eyes", "sneezing", "congestion"]
      }
    },
    {
      name: "create family ent project",
      notebookName: "Family health",
      message: `Create a project note called ${fixtures.titles.familyEnt}. Add a summary and an open question about whether enlarged tonsils could affect sleep.`,
      expect: {
        intent: "CREATE_NOTE",
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        noteType: "project_note/v1",
        sectionId: "open_questions",
        format: "bullet",
        contains: ["tonsils", "sleep"]
      }
    },
    {
      name: "selected sleep note bullet request",
      notebookName: "Family health",
      selectNoteId: fixtures.notes.familySleepLog.id,
      waitForText: fixtures.notes.familySleepLog.title,
      message: "In the body, add a bullet list for bedtime routine issues: late nap, long bath, bright room.",
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.familySleepLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "body",
        format: "bullet",
        contains: ["late nap", "long bath", "bright room"]
      }
    },
    {
      name: "ambiguous family reminder hidden",
      notebookName: "Family health",
      selectNoteId: fixtures.notes.familySleepLog.id,
      waitForText: fixtures.notes.familySleepLog.title,
      message: "Need to remember this at some point.",
      expect: {
        intent: "CREATE_NOTE",
        strategy: "NOTEBOOK_INBOX",
        notebookId: familyId,
        fallbackToInbox: true
      }
    },
    {
      name: "cross-note work from dog selected",
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogTraining.id,
      waitForText: fixtures.notes.dogTraining.title,
      message: `In ${fixtures.notes.workRunbook.title}, add a note to verify Flyway migration order before frontend deploy.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workRunbook.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "body",
        format: "prose",
        contains: ["Flyway migration order", "frontend deploy"]
      }
    },
    {
      name: "cross-note dog from work selected",
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.websiteOps.id,
      waitForText: fixtures.notes.websiteOps.title,
      message: `In ${fixtures.notes.dogMedicationLog.title}, add that appetite was low before the evening dose.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogMedicationLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "body",
        format: "prose",
        contains: ["appetite was low", "evening dose"]
      }
    },
    sequenceCase(fixtures)
  ];
}

function sequenceCase(fixtures) {
  return {
    name: "iterative same note stress",
    notebookName: "Work websites",
    selectNoteId: fixtures.notes.sequenceProject.id,
    waitForText: fixtures.notes.sequenceProject.title,
    sequence: [
      { message: "Status update: local real-model smoke tests are stable.", sectionId: "status", text: "real-model smoke tests are stable" },
      { message: "Add a task to benchmark router latency on 20-note notebooks.", sectionId: "tasks", text: "benchmark router latency" },
      { message: "Add a task to capture token cost per commit.", sectionId: "tasks", text: "capture token cost per commit" },
      { message: "Decision: keep mock AI in unit tests.", sectionId: "decisions", text: "keep mock AI in unit tests" },
      { message: "Open question: should summary refresh stay async?", sectionId: "open_questions", text: "summary refresh stay async" },
      { message: "Add a timeline event for 2026-03-13: finished extended eval prep.", sectionId: "timeline", text: "2026-03-13" },
      { message: "Save this reference: https://platform.openai.com/docs/guides/structured-outputs", sectionId: "references", text: "structured-outputs" },
      { message: "Add a task to watch undo-rate spikes after deploy.", sectionId: "tasks", text: "undo-rate spikes after deploy" },
      { message: "Decision: hide notebook inbox notes from the tab bar.", sectionId: "decisions", text: "hide notebook inbox notes" },
      { message: "Open question: should direct rewrites require stronger confidence?", sectionId: "open_questions", text: "stronger confidence" },
      { message: "Add a timeline event for 2026-03-14: reviewed note-level diffs.", sectionId: "timeline", text: "2026-03-14" },
      { message: "Add a task to log route confidence in analytics.", sectionId: "tasks", text: "route confidence in analytics" },
      { message: "Decision: preserve quoted phrases and exact measurements.", sectionId: "decisions", text: "quoted phrases and exact measurements" },
      { message: "Add a reference: https://platform.openai.com/docs/guides/prompt-engineering", sectionId: "references", text: "prompt-engineering" },
      { message: "Status update: iterative writes stayed on the same note.", sectionId: "status", text: "iterative writes stayed on the same note" },
      { message: "Add a task to add nightly eval snapshots.", sectionId: "tasks", text: "nightly eval snapshots" },
      { message: "Open question: when should the planner escalate models?", sectionId: "open_questions", text: "planner escalate models" },
      { message: "Add a timeline event for 2026-03-15: tuned create-note guardrails.", sectionId: "timeline", text: "2026-03-15" },
      { message: "Decision: vague captures go to notebook inbox.", sectionId: "decisions", text: "vague captures go to notebook inbox" },
      { message: "Open question: should repeated status updates replace instead of append?", sectionId: "open_questions", text: "replace instead of append" }
    ],
    expect: {
      intent: "WRITE_EXISTING_NOTE",
      targetNoteId: fixtures.notes.sequenceProject.id,
      strategy: "DIRECT_APPLY",
      sectionChecks: {
        status: ["real-model smoke tests are stable", "iterative writes stayed on the same note"],
        tasks: ["benchmark router latency", "token cost per commit", "undo-rate spikes", "route confidence", "nightly eval snapshots"],
        decisions: ["keep mock AI in unit tests", "hide notebook inbox notes", "quoted phrases and exact measurements", "vague captures go to notebook inbox"],
        open_questions: ["summary refresh stay async", "stronger confidence", "planner escalate models", "replace instead of append"],
        timeline: ["2026-03-13", "2026-03-14", "2026-03-15"],
        references: ["structured-outputs", "prompt-engineering"]
      },
      visibleContainsAny: ["iterative writes stayed on the same note", "nightly eval snapshots"]
    }
  };
}

function journeyScenario(fixtures) {
  const workId = fixtures.notebookByName["Work websites"].id;
  const dogId = fixtures.notebookByName["Dog notes"].id;
  const familyId = fixtures.notebookByName["Family health"].id;
  const steps = [];

  const pushStep = (step) => {
    if (steps.length < journeyStepCount) {
      steps.push(step);
    }
  };

  for (let cycle = 1; steps.length < journeyStepCount; cycle++) {
    const cycleTag = `J${String(cycle).padStart(3, "0")}`;
    const cycleSlug = cycleTag.toLowerCase();
    const createMode = cycle % 3;
    const workCreateTitle = scopedTitle(`Launch Prep ${cycleTag}`);
    const dogCreateTitle = scopedTitle(`Storm Notes ${cycleTag}`);
    const familyCreateTitle = scopedTitle(`Sleep Follow-up ${cycleTag}`);

    pushStep({
      name: `${cycleTag} work launch task`,
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: `Add a task to this note to review ${cycleTag} router drift before launch.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workLaunch.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "tasks",
        format: "checklist",
        contains: [cycleTag, "router drift"]
      },
      meta: { category: "selected_note_update", cycle, pattern: "work_launch_task" }
    });

    pushStep({
      name: `${cycleTag} work growth status`,
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workGrowthPlan.id,
      waitForText: fixtures.notes.workGrowthPlan.title,
      message: `Status update: ${cycleTag} onboarding cohort is waiting on results.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workGrowthPlan.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "status",
        format: "prose",
        contains: [cycleTag, "waiting on results"]
      },
      meta: { category: "selected_note_update", cycle, pattern: "work_growth_status" }
    });

    pushStep({
      name: `${cycleTag} work runbook retarget`,
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: `In ${fixtures.notes.workRunbook.title}, add a note that ${cycleTag} requires checking migration order before frontend deploy.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workRunbook.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "body",
        format: "prose",
        contains: [cycleTag, "migration order", "frontend deploy"]
      },
      meta: { category: "explicit_retarget", cycle, pattern: "work_runbook_retarget" }
    });

    pushStep({
      name: `${cycleTag} website ops reference`,
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.websiteOps.id,
      waitForText: fixtures.notes.websiteOps.title,
      message: `Save this docs link in the references section: https://example.com/${cycleSlug}/structured-routing`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.websiteOps.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "references",
        format: "bullet",
        contains: [cycleSlug, "structured-routing", "https://example.com/"]
      },
      meta: { category: "section_format", cycle, pattern: "website_ops_reference" }
    });

    pushStep({
      name: `${cycleTag} work vague inbox`,
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workRunbook.id,
      waitForText: fixtures.notes.workRunbook.title,
      message: "Remember this for later.",
      expect: {
        intent: "CREATE_NOTE",
        strategy: "NOTEBOOK_INBOX",
        notebookId: workId,
        fallbackToInbox: true
      },
      meta: { category: "vague_inbox", cycle, pattern: "work_inbox" }
    });

    pushStep({
      name: `${cycleTag} work rewrite`,
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.apiIntegrations.id,
      waitForText: fixtures.notes.apiIntegrations.title,
      message: `Rewrite this note into a cleaner summary with next steps. Preserve the exact phrase "${cycleTag} integration review".`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.apiIntegrations.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        opAnyOf: ["REPLACE_NOTE_OUTLINE", "REPLACE_SECTION_CONTENT"],
        visibleContainsAny: [cycleTag, "integration review"]
      },
      meta: {
        category: "rewrite",
        cycle,
        pattern: "work_rewrite",
        protectedTokens: [`${cycleTag} integration review`]
      }
    });

    pushStep({
      name: `${cycleTag} dog medication dosage`,
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogMedicationLog.id,
      waitForText: fixtures.notes.dogMedicationLog.title,
      message: `Add to the body: trazodone 16 mg at 7:05pm during ${cycleTag} storm prep.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogMedicationLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "body",
        format: "prose",
        contains: [cycleTag, "trazodone", "16 mg", "7:05"]
      },
      meta: {
        category: "protected_fact",
        cycle,
        pattern: "dog_medication_dose",
        protectedTokens: [cycleTag, "trazodone", "16 mg", "7:05pm"]
      }
    });

    pushStep({
      name: `${cycleTag} dog vet action`,
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogVetLog.id,
      waitForText: fixtures.notes.dogVetLog.title,
      message: `Add an action item to ask the vet about ${cycleTag} appetite changes.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogVetLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "action_items",
        format: "checklist",
        contains: [cycleTag, "appetite changes", "vet"]
      },
      meta: { category: "selected_note_update", cycle, pattern: "dog_vet_action" }
    });

    pushStep({
      name: `${cycleTag} dog training retarget`,
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogGear.id,
      waitForText: fixtures.notes.dogGear.title,
      message: `In ${fixtures.notes.dogTraining.title}, add a decision to use a longer leash during ${cycleTag} decompression walks.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogTraining.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "decisions",
        format: "bullet",
        contains: [cycleTag, "longer leash", "decompression walks"]
      },
      meta: { category: "explicit_retarget", cycle, pattern: "dog_training_retarget" }
    });

    pushStep({
      name: `${cycleTag} dog vague inbox`,
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogVetLog.id,
      waitForText: fixtures.notes.dogVetLog.title,
      message: "Need to remember this before the weekend.",
      expect: {
        intent: "CREATE_NOTE",
        strategy: "NOTEBOOK_INBOX",
        notebookId: dogId,
        fallbackToInbox: true
      },
      meta: { category: "vague_inbox", cycle, pattern: "dog_inbox" }
    });

    pushStep({
      name: `${cycleTag} dog quoted phrase`,
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogVetLog.id,
      waitForText: fixtures.notes.dogVetLog.title,
      message: `Add this observation to the body exactly: "${cycleTag} wouldn't settle for 30 minutes after the thunder".`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.dogVetLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: dogId,
        sectionId: "body",
        format: "prose",
        contains: [cycleTag, "wouldn't settle for 30 minutes after the thunder"]
      },
      meta: {
        category: "protected_fact",
        cycle,
        pattern: "dog_quoted_phrase",
        protectedTokens: [cycleTag, "wouldn't settle for 30 minutes after the thunder"]
      }
    });

    pushStep({
      name: `${cycleTag} family fever timing`,
      notebookName: "Family health",
      selectNoteId: fixtures.notes.feverTracking.id,
      waitForText: fixtures.notes.feverTracking.title,
      message: `Add to the body: ibuprofen 5 mL at 19:15 during ${cycleTag}, then temperature 37.9C thirty minutes later.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.feverTracking.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "body",
        format: "prose",
        contains: [cycleTag, "ibuprofen", "5 mL", "19:15", "37.9C"]
      },
      meta: {
        category: "protected_fact",
        cycle,
        pattern: "family_fever_timing",
        protectedTokens: [cycleTag, "ibuprofen", "5 mL", "19:15", "37.9C"]
      }
    });

    pushStep({
      name: `${cycleTag} pediatrician task`,
      notebookName: "Family health",
      selectNoteId: fixtures.notes.pediatrician.id,
      waitForText: fixtures.notes.pediatrician.title,
      message: `Add a task to bring a short video of the ${cycleTag} cough to the appointment.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.pediatrician.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "tasks",
        format: "checklist",
        contains: [cycleTag, "video", "appointment"]
      },
      meta: { category: "selected_note_update", cycle, pattern: "pediatrician_task" }
    });

    pushStep({
      name: `${cycleTag} family sleep retarget`,
      notebookName: "Family health",
      selectNoteId: fixtures.notes.pediatrician.id,
      waitForText: fixtures.notes.pediatrician.title,
      message: `In ${fixtures.notes.familySleepLog.title}, add that ${cycleTag} bedtime moved to 8:40pm and wakeup was 5:55am.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.familySleepLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "body",
        format: "prose",
        contains: [cycleTag, "8:40", "5:55"]
      },
      meta: {
        category: "explicit_retarget",
        cycle,
        pattern: "family_sleep_retarget",
        protectedTokens: [cycleTag, "8:40pm", "5:55am"]
      }
    });

    pushStep({
      name: `${cycleTag} family vague inbox`,
      notebookName: "Family health",
      selectNoteId: fixtures.notes.familySleepLog.id,
      waitForText: fixtures.notes.familySleepLog.title,
      message: "Need to remember this at some point.",
      expect: {
        intent: "CREATE_NOTE",
        strategy: "NOTEBOOK_INBOX",
        notebookId: familyId,
        fallbackToInbox: true
      },
      meta: { category: "vague_inbox", cycle, pattern: "family_inbox" }
    });

    pushStep({
      name: `${cycleTag} family sleep bullets`,
      notebookName: "Family health",
      selectNoteId: fixtures.notes.familySleepLog.id,
      waitForText: fixtures.notes.familySleepLog.title,
      message: `In the body, add a bullet list for ${cycleTag} bedtime issues: late nap, long bath, bright room.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.familySleepLog.id,
        strategy: "DIRECT_APPLY",
        notebookId: familyId,
        sectionId: "body",
        format: "bullet",
        contains: [cycleTag, "late nap", "bright room"]
      },
      meta: { category: "section_format", cycle, pattern: "family_sleep_bullets" }
    });

    pushStep({
      name: `${cycleTag} sequence decision`,
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.sequenceProject.id,
      waitForText: fixtures.notes.sequenceProject.title,
      message: `Decision: keep ${cycleTag} journaling in a single running note.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.sequenceProject.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "decisions",
        format: "bullet",
        contains: [cycleTag, "single running note"]
      },
      meta: { category: "selected_note_update", cycle, pattern: "sequence_decision" }
    });

    pushStep({
      name: `${cycleTag} cross notebook runbook`,
      notebookName: "Dog notes",
      selectNoteId: fixtures.notes.dogTraining.id,
      waitForText: fixtures.notes.dogTraining.title,
      message: `In ${fixtures.notes.workRunbook.title}, add a note that ${cycleTag} requires checking Flyway order before frontend deploy.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workRunbook.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "body",
        format: "prose",
        contains: [cycleTag, "Flyway", "frontend deploy"]
      },
      meta: { category: "cross_notebook", cycle, pattern: "cross_notebook_runbook" }
    });

    if (createMode === 1) {
      pushStep({
        name: `${cycleTag} create work note`,
        notebookName: "Work websites",
        selectNoteId: fixtures.notes.websiteOps.id,
        waitForText: fixtures.notes.websiteOps.title,
        message: `Create a project note called ${workCreateTitle}. Add a summary that ${cycleTag} tracks launch prep and a task to log wrong-target writes.`,
        expect: {
          intent: "CREATE_NOTE",
          strategy: "DIRECT_APPLY",
          notebookId: workId,
          noteType: "project_note/v1",
          targetNoteTitle: workCreateTitle,
          sectionId: "tasks",
          format: "checklist",
          contains: [cycleTag, "wrong-target writes"]
        },
        meta: { category: "create_note", cycle, pattern: "create_work_project" }
      });
    } else if (createMode === 2) {
      pushStep({
        name: `${cycleTag} create dog note`,
        notebookName: "Dog notes",
        selectNoteId: fixtures.notes.dogGear.id,
        waitForText: fixtures.notes.dogGear.title,
        message: `Create a note called ${dogCreateTitle}. Add a summary and mention pacing plus hiding during ${cycleTag} thunder in the body.`,
        expect: {
          intent: "CREATE_NOTE",
          strategy: "DIRECT_APPLY",
          notebookId: dogId,
          noteType: "generic_note/v1",
          targetNoteTitle: dogCreateTitle,
          sectionId: "body",
          format: "prose",
          contains: [cycleTag, "pacing", "hiding"]
        },
        meta: { category: "create_note", cycle, pattern: "create_dog_generic" }
      });
    } else {
      pushStep({
        name: `${cycleTag} create family note`,
        notebookName: "Family health",
        selectNoteId: fixtures.notes.familySleepLog.id,
        waitForText: fixtures.notes.familySleepLog.title,
        message: `Create a project note called ${familyCreateTitle}. Add a summary that ${cycleTag} tracks sleep follow-up and an open question about whether enlarged tonsils affect sleep.`,
        expect: {
          intent: "CREATE_NOTE",
          strategy: "DIRECT_APPLY",
          notebookId: familyId,
          noteType: "project_note/v1",
          targetNoteTitle: familyCreateTitle,
          sectionId: "open_questions",
          format: "bullet",
          contains: [cycleTag, "tonsils", "sleep"]
        },
        meta: { category: "create_note", cycle, pattern: "create_family_project" }
      });
    }

    pushStep({
      name: `${cycleTag} work launch timeline`,
      notebookName: "Work websites",
      selectNoteId: fixtures.notes.workLaunch.id,
      waitForText: fixtures.notes.workLaunch.title,
      message: `Add a timeline event for 2026-03-30: ${cycleTag} launch review completed.`,
      expect: {
        intent: "WRITE_EXISTING_NOTE",
        targetNoteId: fixtures.notes.workLaunch.id,
        strategy: "DIRECT_APPLY",
        notebookId: workId,
        sectionId: "timeline",
        format: "bullet",
        contains: ["2026-03-30", cycleTag, "launch review completed"]
      },
      meta: { category: "selected_note_update", cycle, pattern: "work_launch_timeline" }
    });
  }

  return {
    name: `first-user-journey-${steps.length}-writes`,
    journeySteps: steps
  };
}

function cases(fixtures) {
  const suites = {
    base: baseCases(fixtures),
    extended: extendedCases(fixtures),
    all: [...baseCases(fixtures), ...extendedCases(fixtures)],
    journey: [journeyScenario(fixtures)]
  };
  return suites[evalSuite] ?? suites.base;
}

async function waitForEditorText(page, text) {
  const found = await page.locator("[data-testid='note-editor']").filter({ hasText: text }).first()
    .waitFor({ timeout: 20000 })
    .then(() => true)
    .catch(() => false);
  if (!found) {
    throw new Error(`Editor did not show expected text within 20s: "${text}"`);
  }
}

async function editorText(page) {
  return (await page.getByTestId("note-editor").textContent()) ?? "";
}

async function selectNotebook(page, notebookName) {
  // Try dropdown-style notebook selector first (current UI), then fall back to button-style (legacy UI)
  const selectLocator = page.locator("select").filter({ hasText: notebookName }).first();
  const anySelect = page.locator("select").first();
  const hasSelect = await anySelect.waitFor({ state: "visible", timeout: 15000 }).then(() => true).catch(() => false);
  if (hasSelect) {
    await anySelect.selectOption({ label: notebookName });
  } else {
    await page.getByRole("button", { name: new RegExp(escapeRegex(notebookName), "i") }).click();
  }
  await page.waitForTimeout(600);
}

async function selectNote(page, noteId, waitForTextHint) {
  await page.getByTestId(`note-tab-${noteId}`).click();
  if (waitForTextHint) {
    await waitForEditorText(page, waitForTextHint);
  } else {
    await page.waitForTimeout(800);
  }
}

function findSection(note, sectionId) {
  return note?.document?.sections?.find((section) => section.id === sectionId) ?? null;
}

function formatPass(section, expectedFormat) {
  if (!section) return false;
  const content = section.contentMarkdown ?? "";
  switch (expectedFormat) {
    case "checklist":
      return /- \[ \] /.test(content);
    case "bullet":
      return /^\s*- /m.test(content);
    case "prose":
      return content.trim().length > 0 && !content.trim().startsWith("- ");
    default:
      return true;
  }
}

function includesAllCaseInsensitive(haystack, needles) {
  const normalizedHaystack = (haystack ?? "").toLowerCase();
  return (needles ?? []).every((needle) => normalizedHaystack.includes((needle ?? "").toLowerCase()));
}

async function verifyUi(page, commit, scenario) {
  try {
    return await verifyUiInner(page, commit, scenario);
  } catch (error) {
    return { pass: false, detail: String(error?.message ?? error) };
  }
}

async function verifyUiInner(page, commit, scenario) {
  if (scenario.expect.intent === "ANSWER_ONLY") {
    const answer = commit.answer ?? "";
    if (!answer) {
      return { pass: false, detail: "No answer text returned." };
    }
    const snippet = answer.slice(0, Math.min(32, answer.length));
    await page.getByText(snippet, { exact: false }).last().waitFor({ timeout: 15000 });
    return { pass: true, detail: `Chat showed answer snippet "${snippet}"` };
  }

  const isInboxNote = commit.updatedNote?.title?.toLowerCase() === "inbox"
    || commit.routePlan?.strategy === "NOTEBOOK_INBOX"
    || commit.routePlan?.strategy === "NOTE_INBOX";
  if (commit.updatedNote && !isInboxNote) {
    await page.getByTestId(`note-tab-${commit.updatedNote.id}`).waitFor({ timeout: 15000 });
    if (scenario.expect.visibleContains?.length) {
      for (const text of scenario.expect.visibleContains) {
        await waitForEditorText(page, text);
      }
    } else if (scenario.expect.visibleContainsAny?.length) {
      const text = await editorText(page);
      if (!scenario.expect.visibleContainsAny.some((candidate) => text.toLowerCase().includes(candidate.toLowerCase()))) {
        return {
          pass: false,
          detail: `Editor did not contain any of: ${scenario.expect.visibleContainsAny.join(", ")}`
        };
      }
    } else if (scenario.expect.contains?.length) {
      for (const text of scenario.expect.contains) {
        await waitForEditorText(page, text);
      }
    }
    return { pass: true, detail: `Visible note "${commit.updatedNote.title}" updated in UI.` };
  }

  if (scenario.expect.fallbackToInbox) {
    const tabTexts = await page.locator("[data-testid^='note-tab-']").allInnerTexts();
    const showsInboxTab = tabTexts.some((text) => text.trim().toLowerCase() === "inbox");
    if (showsInboxTab) {
      return { pass: false, detail: "Hidden inbox note became visible in the tab bar." };
    }
    await page.getByText(/Queued/i).last().waitFor({ timeout: 15000 });
    return { pass: true, detail: "UI kept inbox hidden and showed queued state." };
  }

  return { pass: true, detail: "No UI-specific assertion required." };
}

function slugify(value) {
  return (value ?? "artifact")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80) || "artifact";
}

async function installCommitDebugTraceRoute(page) {
  if (!includeDebugTrace) {
    return;
  }

  await page.route(/\/api\/v2\/chat-events\/commit(?:\?|$)/, async (route) => {
    const request = route.request();
    const headers = { ...request.headers(), "content-type": "application/json" };
    delete headers["content-length"];

    let postData = request.postData() ?? "{}";
    try {
      const body = JSON.parse(postData);
      body.includeDebugTrace = true;
      postData = JSON.stringify(body);
    } catch {
      // If the client payload is not valid JSON, leave it untouched.
    }

    await route.continue({ headers, postData });
  });
}

function routePassForScenario(commit, scenario) {
  const routePlan = commit.routePlan ?? {};
  const patchPlan = commit.patchPlan ?? {};
  return (
    (!scenario.expect.intent || routePlan.intent === scenario.expect.intent) &&
    (!scenario.expect.strategy || routePlan.strategy === scenario.expect.strategy) &&
    (!scenario.expect.targetNoteId || routePlan.targetNoteId === scenario.expect.targetNoteId) &&
    (!scenario.expect.notebookId || routePlan.targetNotebookId === scenario.expect.notebookId) &&
    (!scenario.expect.noteType || routePlan.targetNoteType === scenario.expect.noteType) &&
    (!scenario.expect.fallbackToInbox || patchPlan.fallbackToInbox === true)
  );
}

function contentPassForScenario(commit, updatedNote, scenario) {
  let contentPass = true;
  const detailParts = [];

  if (scenario.expect.targetNoteTitle) {
    const actualTitle = commit.updatedNote?.title ?? null;
    const titlePass = actualTitle === scenario.expect.targetNoteTitle;
    contentPass = contentPass && titlePass;
    detailParts.push(`title: ${actualTitle}`);
  }

  if (scenario.expect.sectionId) {
    const section = findSection(updatedNote, scenario.expect.sectionId);
    const sectionPass =
      Boolean(section) &&
      formatPass(section, scenario.expect.format) &&
      includesAllCaseInsensitive(section?.contentMarkdown, scenario.expect.contains) &&
      (
        !scenario.expect.containsAny?.length ||
        scenario.expect.containsAny.some((candidate) =>
          (section?.contentMarkdown ?? "").toLowerCase().includes(candidate.toLowerCase())
        )
      );
    contentPass = contentPass && sectionPass;
    detailParts.push(section ? `${scenario.expect.sectionId}: ${section.contentMarkdown}` : `Section ${scenario.expect.sectionId} missing`);
  }

  if (scenario.expect.answerContains?.length) {
    const answerPass = scenario.expect.answerContains.every((value) =>
      (commit.answer ?? "").toLowerCase().includes(value.toLowerCase())
    );
    contentPass = contentPass && answerPass;
    detailParts.push(commit.answer ?? "");
  }

  if (scenario.expect.answerContainsAny?.length) {
    const answerPass = scenario.expect.answerContainsAny.some((value) =>
      (commit.answer ?? "").toLowerCase().includes(value.toLowerCase())
    );
    contentPass = contentPass && answerPass;
    detailParts.push(commit.answer ?? "");
  }

  if (scenario.expect.op || scenario.expect.opAnyOf?.length) {
    const firstOp = commit.patchPlan?.ops?.[0]?.op ?? null;
    const opPass = scenario.expect.opAnyOf?.length
      ? scenario.expect.opAnyOf.includes(firstOp)
      : firstOp === scenario.expect.op;
    contentPass = contentPass && opPass;
    detailParts.push(`First op: ${firstOp}`);
  }

  return {
    pass: contentPass,
    detail: detailParts.filter(Boolean).join("\n") || "No content assertion required."
  };
}

function failureBucketsForScenario(scenario, commit, updatedNote, routePass, content, ui) {
  const buckets = new Set();
  const routePlan = commit.routePlan ?? {};
  const patchPlan = commit.patchPlan ?? {};

  if (!commit.routePlan) {
    buckets.add("missing_route_plan");
  }
  if (scenario.expect.intent && routePlan.intent !== scenario.expect.intent) {
    buckets.add("wrong_intent");
  }
  if (scenario.expect.strategy && routePlan.strategy !== scenario.expect.strategy) {
    buckets.add("wrong_strategy");
  }
  if (scenario.expect.targetNoteId && routePlan.targetNoteId !== scenario.expect.targetNoteId) {
    buckets.add("wrong_target_note");
  }
  if (scenario.expect.notebookId && routePlan.targetNotebookId !== scenario.expect.notebookId) {
    buckets.add("wrong_notebook");
  }
  if (scenario.expect.noteType && routePlan.targetNoteType !== scenario.expect.noteType) {
    buckets.add("wrong_note_type");
  }
  if (scenario.expect.fallbackToInbox && patchPlan.fallbackToInbox !== true) {
    buckets.add("inbox_underuse");
  }
  if (!scenario.expect.fallbackToInbox && patchPlan.fallbackToInbox === true && scenario.expect.intent !== "ANSWER_ONLY") {
    buckets.add("inbox_overuse");
  }
  if (scenario.expect.targetNoteTitle && commit.updatedNote?.title !== scenario.expect.targetNoteTitle) {
    buckets.add("create_title_mismatch");
  }
  if (scenario.expect.targetNoteId && commit.updatedNote?.id != null && commit.updatedNote.id !== scenario.expect.targetNoteId) {
    buckets.add("wrong_updated_note");
  }
  if (scenario.expect.sectionId) {
    const section = findSection(updatedNote, scenario.expect.sectionId);
    if (!section) {
      buckets.add("wrong_section");
    } else {
      if (!formatPass(section, scenario.expect.format)) {
        buckets.add("wrong_format");
      }
      if (!includesAllCaseInsensitive(section.contentMarkdown, scenario.expect.contains)) {
        buckets.add("missing_content");
      }
      if (
        scenario.expect.containsAny?.length &&
        !scenario.expect.containsAny.some((candidate) =>
          (section.contentMarkdown ?? "").toLowerCase().includes(candidate.toLowerCase())
        )
      ) {
        buckets.add("missing_variant_content");
      }
      if (
        scenario.meta?.protectedTokens?.some(
          (token) => !((section.contentMarkdown ?? "").toLowerCase().includes((token ?? "").toLowerCase()))
        )
      ) {
        buckets.add("protected_token_loss");
      }
    }
  }
  if (scenario.expect.op || scenario.expect.opAnyOf?.length) {
    const firstOp = commit.patchPlan?.ops?.[0]?.op ?? null;
    const opPass = scenario.expect.opAnyOf?.length
      ? scenario.expect.opAnyOf.includes(firstOp)
      : firstOp === scenario.expect.op;
    if (!opPass) {
      buckets.add("wrong_op");
    }
  }
  if (!ui.pass) {
    buckets.add(scenario.expect.fallbackToInbox ? "inbox_visibility" : "ui_mismatch");
  }
  if (!routePass && buckets.size === 0) {
    buckets.add("route_mismatch");
  }
  if (!content.pass && buckets.size === 0) {
    buckets.add("content_mismatch");
  }

  return Array.from(buckets);
}

function summarizedPatchPlan(commit) {
  return {
    fallbackToInbox: commit.patchPlan?.fallbackToInbox,
    ops: commit.patchPlan?.ops?.map((op) => ({
      op: op.op,
      sectionId: op.sectionId,
      title: op.title,
      contentMarkdown: op.contentMarkdown
    }))
  };
}

function usageFromTrace(debugTrace) {
  const routeUsage = debugTrace?.route?.usage ?? {};
  const patchUsage = debugTrace?.patch?.usage ?? {};
  const routeCost = routeUsage.estimatedCostUsd;
  const patchCost = patchUsage.estimatedCostUsd;
  const estimatedCostUsd =
    routeCost == null && patchCost == null
      ? null
      : (routeCost ?? 0) + (patchCost ?? 0);
  return {
    inputTokens: (routeUsage.inputTokens ?? 0) + (patchUsage.inputTokens ?? 0),
    outputTokens: (routeUsage.outputTokens ?? 0) + (patchUsage.outputTokens ?? 0),
    totalTokens: (routeUsage.totalTokens ?? 0) + (patchUsage.totalTokens ?? 0),
    reasoningTokens: (routeUsage.reasoningTokens ?? 0) + (patchUsage.reasoningTokens ?? 0),
    cachedInputTokens: (routeUsage.cachedInputTokens ?? 0) + (patchUsage.cachedInputTokens ?? 0),
    estimatedCostUsd
  };
}

function latencyFromTrace(debugTrace) {
  return {
    retrievalMs: debugTrace?.retrieval?.latencyMs ?? 0,
    routeMs: debugTrace?.route?.latencyMs ?? 0,
    patchMs: debugTrace?.patch?.latencyMs ?? 0,
    mutationMs: debugTrace?.mutationLatencyMs ?? 0
  };
}

function addTotals(accumulator, delta) {
  accumulator.inputTokens += delta.inputTokens ?? 0;
  accumulator.outputTokens += delta.outputTokens ?? 0;
  accumulator.totalTokens += delta.totalTokens ?? 0;
  accumulator.reasoningTokens += delta.reasoningTokens ?? 0;
  accumulator.cachedInputTokens += delta.cachedInputTokens ?? 0;
  if (delta.estimatedCostUsd != null) {
    accumulator.estimatedCostUsd += delta.estimatedCostUsd;
    accumulator.estimatedCostSeen = true;
  }
}

function makeUsageAccumulator() {
  return {
    inputTokens: 0,
    outputTokens: 0,
    totalTokens: 0,
    reasoningTokens: 0,
    cachedInputTokens: 0,
    estimatedCostUsd: 0,
    estimatedCostSeen: false
  };
}

async function submitChatViaUi(page, message) {
  // Open chat panel if it's collapsed behind the FAB (new UI)
  const fab = page.getByTestId("chat-fab");
  const fabVisible = await fab.isVisible().catch(() => false);
  if (fabVisible) {
    await fab.click();
    await page.waitForTimeout(300);
  }

  const commitResponsePromise = page.waitForResponse((response) =>
    response.url().includes("/api/v2/chat-events/commit") &&
    response.request().method() === "POST"
  , { timeout: 90000 });

  const input = page.getByTestId("chat-input");
  await input.waitFor({ state: "visible", timeout: 10000 });
  await input.fill(message);
  await input.press("Enter");

  const commitResponse = await commitResponsePromise;
  const commitBody = await commitResponse.text();
  if (!commitResponse.ok()) {
    throw new Error(commitBody || `Chat commit failed with ${commitResponse.status()}`);
  }
  const commit = commitBody ? JSON.parse(commitBody) : {};
  await page.waitForTimeout(1200);
  return commit;
}

function aggregateLatency(results, key) {
  const values = results
    .map((result) => result.latencyMs?.[key] ?? 0)
    .filter((value) => Number.isFinite(value) && value > 0);
  if (values.length === 0) {
    return { average: 0, max: 0 };
  }
  const total = values.reduce((sum, value) => sum + value, 0);
  return {
    average: Math.round(total / values.length),
    max: Math.max(...values)
  };
}

function rollingWindows(results, windowSize) {
  if (windowSize <= 0) {
    return [];
  }
  const windows = [];
  for (let index = 0; index < results.length; index += windowSize) {
    const slice = results.slice(index, index + windowSize);
    const failed = slice.filter((result) => !result.pass);
    const bucketCounts = {};
    for (const result of failed) {
      for (const bucket of result.failureBuckets ?? []) {
        bucketCounts[bucket] = (bucketCounts[bucket] ?? 0) + 1;
      }
    }
    windows.push({
      startStep: slice[0]?.step ?? index + 1,
      endStep: slice[slice.length - 1]?.step ?? index + slice.length,
      passed: slice.filter((result) => result.pass).length,
      failed: failed.length,
      passRate: slice.length === 0 ? 0 : Number((slice.filter((result) => result.pass).length / slice.length).toFixed(4)),
      bucketCounts
    });
  }
  return windows;
}

function longestFailureStreak(results) {
  let best = { length: 0, startStep: null, endStep: null };
  let currentLength = 0;
  let currentStart = null;

  for (const result of results) {
    if (!result.pass) {
      currentLength += 1;
      if (currentStart == null) {
        currentStart = result.step;
      }
      if (currentLength > best.length) {
        best = { length: currentLength, startStep: currentStart, endStep: result.step };
      }
    } else {
      currentLength = 0;
      currentStart = null;
    }
  }

  return best;
}

function journeyReport(scenario, results, options) {
  const passed = results.filter((result) => result.pass).length;
  const failed = results.length - passed;
  const bucketCounts = {};
  const categoryCounts = {};
  const usage = makeUsageAccumulator();

  for (const result of results) {
    const category = result.category ?? "unknown";
    categoryCounts[category] = (categoryCounts[category] ?? 0) + 1;
    addTotals(usage, result.usage ?? {});
    for (const bucket of result.failureBuckets ?? []) {
      bucketCounts[bucket] = (bucketCounts[bucket] ?? 0) + 1;
    }
  }

  const firstFailure = results.find((result) => !result.pass) ?? null;
  const firstCriticalFailure = results.find((result) =>
    (result.failureBuckets ?? []).some((bucket) =>
      ["wrong_target_note", "wrong_notebook", "wrong_strategy", "protected_token_loss", "create_title_mismatch"].includes(bucket)
    )
  ) ?? null;

  const slowestSteps = [...results]
    .sort((left, right) =>
      ((right.latencyMs?.retrievalMs ?? 0) + (right.latencyMs?.routeMs ?? 0) + (right.latencyMs?.patchMs ?? 0) + (right.latencyMs?.mutationMs ?? 0)) -
      ((left.latencyMs?.retrievalMs ?? 0) + (left.latencyMs?.routeMs ?? 0) + (left.latencyMs?.patchMs ?? 0) + (left.latencyMs?.mutationMs ?? 0))
    )
    .slice(0, 10)
    .map((result) => ({
      step: result.step,
      name: result.name,
      totalMs:
        (result.latencyMs?.retrievalMs ?? 0) +
        (result.latencyMs?.routeMs ?? 0) +
        (result.latencyMs?.patchMs ?? 0) +
        (result.latencyMs?.mutationMs ?? 0),
      failureBuckets: result.failureBuckets
    }));

  return {
    suite: "journey",
    runTag,
    backendUrl,
    frontendUrl,
    includeDebugTrace,
    plannedSteps: scenario.journeySteps.length,
    completedSteps: results.length,
    passedSteps: passed,
    failedSteps: failed,
    passRate: results.length === 0 ? 0 : Number((passed / results.length).toFixed(4)),
    startedAt: options.startedAt,
    finishedAt: options.finishedAt,
    inProgress: options.inProgress ?? false,
    stopReason: options.stopReason ?? null,
    budgetGuards: {
      maxEstimatedCostUsd,
      maxTotalTokens,
      maxConsecutiveFailures
    },
    usage: {
      inputTokens: usage.inputTokens,
      outputTokens: usage.outputTokens,
      totalTokens: usage.totalTokens,
      reasoningTokens: usage.reasoningTokens,
      cachedInputTokens: usage.cachedInputTokens,
      estimatedCostUsd: usage.estimatedCostSeen ? Number(usage.estimatedCostUsd.toFixed(6)) : null
    },
    latency: {
      retrievalMs: aggregateLatency(results, "retrievalMs"),
      routeMs: aggregateLatency(results, "routeMs"),
      patchMs: aggregateLatency(results, "patchMs"),
      mutationMs: aggregateLatency(results, "mutationMs")
    },
    firstFailure: firstFailure
      ? {
          step: firstFailure.step,
          name: firstFailure.name,
          failureBuckets: firstFailure.failureBuckets
        }
      : null,
    firstCriticalFailure: firstCriticalFailure
      ? {
          step: firstCriticalFailure.step,
          name: firstCriticalFailure.name,
          failureBuckets: firstCriticalFailure.failureBuckets
        }
      : null,
    longestFailureStreak: longestFailureStreak(results),
    rollingWindows: rollingWindows(results, rollingWindowSize),
    bucketCounts,
    categoryCounts,
    slowestSteps,
    results
  };
}

async function writeReport(reportPath, payload) {
  await fs.writeFile(reportPath, JSON.stringify(payload, null, 2));
}

function stopReasonFromUsage(usage, consecutiveFailures) {
  if (maxEstimatedCostUsd != null && usage.estimatedCostSeen && usage.estimatedCostUsd >= maxEstimatedCostUsd) {
    return `estimated_cost_limit_reached:${usage.estimatedCostUsd.toFixed(4)}`;
  }
  if (maxTotalTokens != null && usage.totalTokens >= maxTotalTokens) {
    return `total_token_limit_reached:${usage.totalTokens}`;
  }
  if (maxConsecutiveFailures != null && consecutiveFailures >= maxConsecutiveFailures) {
    return `consecutive_failures_reached:${consecutiveFailures}`;
  }
  return null;
}

async function runSequenceScenario(page, scenario, index) {
  await selectNotebook(page, scenario.notebookName);
  await selectNote(page, scenario.selectNoteId, scenario.waitForText);

  const stepResults = [];
  for (const [stepIndex, step] of scenario.sequence.entries()) {
    const commit = await submitChatViaUi(page, step.message);
    const updatedNote = commit.updatedNote?.id
      ? await request(`/api/v2/notes/${commit.updatedNote.id}`)
      : null;

    const stepPass =
      commit.routePlan?.intent === scenario.expect.intent &&
      commit.routePlan?.strategy === scenario.expect.strategy &&
      commit.routePlan?.targetNoteId === scenario.expect.targetNoteId &&
      commit.updatedNote?.id === scenario.expect.targetNoteId &&
      (step.sectionId == null || includesAllCaseInsensitive(findSection(updatedNote, step.sectionId)?.contentMarkdown, [step.text]));

    stepResults.push({
      step: stepIndex + 1,
      message: step.message,
      pass: stepPass,
      routePlan: commit.routePlan,
      updatedNoteId: commit.updatedNote?.id ?? null
    });

    if (!stepPass) {
      const failurePath = path.resolve(
        scriptDir,
        `eval-real-openai-failure-${String(index + 1).padStart(2, "0")}-step-${String(stepIndex + 1).padStart(2, "0")}.png`
      );
      await page.screenshot({ path: failurePath, fullPage: true }).catch(() => {});
      return {
        case: scenario.name,
        message: `sequence failed at step ${stepIndex + 1}`,
        pass: false,
        routePass: false,
        contentPass: false,
        uiPass: false,
        uiDetail: `Step ${stepIndex + 1} did not stay on note ${scenario.expect.targetNoteId}.`,
        contentDetail: JSON.stringify(stepResults, null, 2),
        routePlan: commit.routePlan,
        patchPlan: commit.patchPlan,
        updatedNote: commit.updatedNote ?? null
      };
    }
  }

  const finalNote = await request(`/api/v2/notes/${scenario.expect.targetNoteId}`);
  let contentPass = true;
  const sectionDetails = [];
  for (const [sectionId, expectedTexts] of Object.entries(scenario.expect.sectionChecks ?? {})) {
    const content = findSection(finalNote, sectionId)?.contentMarkdown ?? "";
    const sectionPass = includesAllCaseInsensitive(content, expectedTexts);
    contentPass = contentPass && sectionPass;
    sectionDetails.push(`${sectionId}: ${content}`);
  }

  const finalEditorText = scenario.expect.visibleContainsAny?.length
    ? await editorText(page)
    : "";
  const uiPass = scenario.expect.visibleContainsAny?.length
    ? scenario.expect.visibleContainsAny.some((candidate) => finalEditorText.toLowerCase().includes(candidate.toLowerCase()))
    : true;

  const overallPass = contentPass && uiPass;
  if (!overallPass) {
    const failurePath = path.resolve(
      scriptDir,
      `eval-real-openai-failure-${String(index + 1).padStart(2, "0")}.png`
    );
    await page.screenshot({ path: failurePath, fullPage: true }).catch(() => {});
  }

  return {
    case: scenario.name,
    message: `${scenario.sequence.length} sequential writes`,
    pass: overallPass,
    routePass: true,
    contentPass,
    uiPass,
    uiDetail: uiPass ? "Sequence stayed visible in the same note." : "Final editor content did not show expected sequence text.",
    contentDetail: sectionDetails.join("\n\n"),
    routePlan: { steps: stepResults },
    patchPlan: null,
    updatedNote: {
      id: finalNote.id,
      title: finalNote.title,
      notebookName: finalNote.notebookName
    }
  };
}

async function runJourneyScenario(page, scenario, index, runState) {
  // Load prior results when resuming so we skip already-completed steps
  let results = [];
  if (resumeMode) {
    try {
      const prior = JSON.parse(await fs.readFile(runState.reportPath, "utf8"));
      if (prior.runTag === runTag && Array.isArray(prior.results)) {
        results = prior.results;
        console.error(`[checkpoint] resuming from step ${results.length + 1} (${results.length} steps already done)`);
      }
    } catch { /* no prior report — start fresh */ }
  }
  const cumulativeUsage = makeUsageAccumulator();
  // Re-accumulate usage from prior results
  for (const r of results) addTotals(cumulativeUsage, r.usage ?? {});
  let consecutiveFailures = 0;
  let stopReason = null;

  for (const [stepIndex, step] of scenario.journeySteps.entries()) {
    // Skip steps already completed in a prior run
    if (stepIndex < results.length) continue;
    try {
      await selectNotebook(page, step.notebookName);
      if (step.selectNoteId) {
        await selectNote(page, step.selectNoteId, step.waitForText);
      }

      const commit = await submitChatViaUi(page, step.message);
      const updatedNote = commit.updatedNote?.id
        ? await request(`/api/v2/notes/${commit.updatedNote.id}`)
        : null;

      const routePass = routePassForScenario(commit, step);
      const content = contentPassForScenario(commit, updatedNote, step);
      const ui = await verifyUi(page, commit, step);
      const pass = routePass && content.pass && ui.pass;
      const failureBuckets = failureBucketsForScenario(step, commit, updatedNote, routePass, content, ui);
      const usage = usageFromTrace(commit.debugTrace);
      const latencyMs = latencyFromTrace(commit.debugTrace);

      addTotals(cumulativeUsage, usage);
      consecutiveFailures = pass ? 0 : consecutiveFailures + 1;

      const stepResult = {
        step: stepIndex + 1,
        name: step.name,
        category: step.meta?.category ?? null,
        cycle: step.meta?.cycle ?? null,
        pattern: step.meta?.pattern ?? null,
        pass,
        failureBuckets,
        notebookName: step.notebookName,
        selectedNoteId: step.selectNoteId ?? null,
        message: step.message,
        expected: step.expect,
        routePass,
        contentPass: content.pass,
        uiPass: ui.pass,
        uiDetail: ui.detail,
        contentDetail: content.detail,
        routePlan: commit.routePlan ?? null,
        patchPlan: summarizedPatchPlan(commit),
        updatedNote: commit.updatedNote
          ? {
              id: commit.updatedNote.id,
              title: commit.updatedNote.title,
              notebookName: commit.updatedNote.notebookName
            }
          : null,
        usage,
        latencyMs,
        debugTrace: commit.debugTrace ?? null
      };
      results.push(stepResult);

      if (!pass) {
        const failurePath = path.resolve(
          scriptDir,
          `eval-real-openai-journey-failure-${String(index + 1).padStart(2, "0")}-${String(stepIndex + 1).padStart(3, "0")}-${slugify(step.name)}.png`
        );
        await page.screenshot({ path: failurePath, fullPage: true }).catch(() => {});
      }

      if (checkpointEvery > 0 && ((stepIndex + 1) % checkpointEvery === 0 || !pass)) {
        await writeReport(
          runState.reportPath,
          journeyReport(scenario, results, {
            startedAt: runState.startedAt,
            finishedAt: null,
            inProgress: true,
            stopReason: null
          })
        );
      }

      stopReason = stopReasonFromUsage(cumulativeUsage, consecutiveFailures);
      if (stopReason) {
        break;
      }
    } catch (error) {
      const failurePath = path.resolve(
        scriptDir,
        `eval-real-openai-journey-error-${String(index + 1).padStart(2, "0")}-${String(stepIndex + 1).padStart(3, "0")}.png`
      );
      await page.screenshot({ path: failurePath, fullPage: true }).catch(() => {});

      results.push({
        step: stepIndex + 1,
        name: step.name,
        category: step.meta?.category ?? null,
        cycle: step.meta?.cycle ?? null,
        pattern: step.meta?.pattern ?? null,
        pass: false,
        failureBuckets: ["exception"],
        notebookName: step.notebookName,
        selectedNoteId: step.selectNoteId ?? null,
        message: step.message,
        expected: step.expect,
        routePass: false,
        contentPass: false,
        uiPass: false,
        uiDetail: String(error.message ?? error),
        contentDetail: "",
        routePlan: null,
        patchPlan: null,
        updatedNote: null,
        usage: usageFromTrace(null),
        latencyMs: latencyFromTrace(null),
        debugTrace: null
      });

      consecutiveFailures++;
      // Only treat as fatal if browser/context is gone — timeouts and transient errors are soft
      const errorMsg = String(error.message ?? error);
      const isFatal = errorMsg.includes("Target page, context or browser has been closed") ||
                      errorMsg.includes("context destroyed") ||
                      errorMsg.includes("Browser has been closed");
      if (isFatal) {
        stopReason = `fatal_exception_step_${stepIndex + 1}`;
        break;
      }
      stopReason = stopReasonFromUsage(cumulativeUsage, consecutiveFailures);
      if (stopReason) break;
      // Navigate back to home to recover from transient errors
      await page.goto(runState.frontendUrl, { timeout: 15000 }).catch(() => {});
      await page.waitForTimeout(1000);
    }
  }

  return journeyReport(scenario, results, {
    startedAt: runState.startedAt,
    finishedAt: new Date().toISOString(),
    inProgress: false,
    stopReason
  });
}

async function runScenario(page, runState, scenario, index) {
  if (scenario.journeySteps?.length) {
    return runJourneyScenario(page, scenario, index, runState);
  }

  if (scenario.sequence?.length) {
    return runSequenceScenario(page, scenario, index);
  }

  await selectNotebook(page, scenario.notebookName);
  if (scenario.selectNoteId) {
    await selectNote(page, scenario.selectNoteId, scenario.waitForText);
  }
  const commit = await submitChatViaUi(page, scenario.message);

  const updatedNote = commit.updatedNote?.id
    ? await request(`/api/v2/notes/${commit.updatedNote.id}`)
    : null;

  const routePass = routePassForScenario(commit, scenario);
  const content = contentPassForScenario(commit, updatedNote, scenario);

  const ui = await verifyUi(page, commit, scenario);

  const overallPass = routePass && content.pass && ui.pass;
  if (!overallPass) {
    const failurePath = path.resolve(
      scriptDir,
      `eval-real-openai-failure-${String(index + 1).padStart(2, "0")}.png`
    );
    await page.screenshot({ path: failurePath, fullPage: true }).catch(() => {});
  }

  return {
    case: scenario.name,
    message: scenario.message,
    pass: overallPass,
    routePass,
    contentPass: content.pass,
    uiPass: ui.pass,
    uiDetail: ui.detail,
    contentDetail: content.detail,
    routePlan: commit.routePlan,
    patchPlan: summarizedPatchPlan(commit),
    updatedNote: commit.updatedNote
      ? {
          id: commit.updatedNote.id,
          title: commit.updatedNote.title,
          notebookName: commit.updatedNote.notebookName
        }
      : null
  };
}

async function run() {
  const fixtures = await seedOrLoadFixtures();
  const scenarios = cases(fixtures).filter((scenario) =>
    requestedCases.length === 0 || requestedCases.includes(scenario.name)
  );
  const reportPath = path.resolve(
    scriptDir,
    evalSuite === "journey" ? "eval-real-openai-journey-last-report.json" : "eval-real-openai-last-report.json"
  );

  const browser = await chromium.launch({
    executablePath: browserPath(),
    headless: true
  });

  const startedAt = new Date().toISOString();
  const results = [];

  try {
    const page = await browser.newPage();
    await installCommitDebugTraceRoute(page);
    await page.goto(frontendUrl, { waitUntil: "networkidle" });

    for (let index = 0; index < scenarios.length; index++) {
      const scenario = scenarios[index];
      try {
        results.push(await runScenario(page, { startedAt, reportPath }, scenario, index));
      } catch (error) {
        const failurePath = path.resolve(
          scriptDir,
          `eval-real-openai-error-${String(index + 1).padStart(2, "0")}.png`
        );
        await page.screenshot({ path: failurePath, fullPage: true }).catch(() => {});
        results.push({
          case: scenario.name,
          message: scenario.message,
          pass: false,
          routePass: false,
          contentPass: false,
          uiPass: false,
          uiDetail: String(error.message ?? error),
          contentDetail: "",
          routePlan: null,
          patchPlan: null,
          updatedNote: null
        });
        await page.goto(frontendUrl, { waitUntil: "networkidle" });
      }
    }
  } finally {
    await browser.close();
  }

  if (evalSuite === "journey" && results[0]?.suite === "journey") {
    await writeReport(reportPath, results[0]);
    console.log(JSON.stringify(results[0], null, 2));
    return;
  }

  const summary = {
    startedAt,
    finishedAt: new Date().toISOString(),
    backendUrl,
    frontendUrl,
    total: results.length,
    passed: results.filter((result) => result.pass).length,
    failed: results.filter((result) => !result.pass).length,
    results
  };

  await writeReport(reportPath, summary);
  console.log(JSON.stringify(summary, null, 2));
}

run().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
