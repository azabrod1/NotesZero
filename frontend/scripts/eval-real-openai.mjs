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
const runTag = process.env.EVAL_RUN_TAG ?? new Date().toISOString().replace(/\D/g, "").slice(0, 12);

function fail(message) {
  throw new Error(message);
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

  return { notebookByName, notes: created, titles };
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
        op: "REPLACE_NOTE_OUTLINE",
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

function cases(fixtures) {
  const suites = {
    base: baseCases(fixtures),
    extended: extendedCases(fixtures),
    all: [...baseCases(fixtures), ...extendedCases(fixtures)]
  };
  return suites[evalSuite] ?? suites.base;
}

async function waitForEditorText(page, text) {
  await page.locator("[data-testid='note-editor']").filter({ hasText: text }).first().waitFor({
    timeout: 20000
  });
}

async function editorText(page) {
  return (await page.getByTestId("note-editor").textContent()) ?? "";
}

async function selectNotebook(page, notebookName) {
  await page.getByRole("button", { name: new RegExp(escapeRegex(notebookName), "i") }).click();
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
  if (scenario.expect.intent === "ANSWER_ONLY") {
    const answer = commit.answer ?? "";
    if (!answer) {
      return { pass: false, detail: "No answer text returned." };
    }
    const snippet = answer.slice(0, Math.min(32, answer.length));
    await page.getByText(snippet, { exact: false }).last().waitFor({ timeout: 15000 });
    return { pass: true, detail: `Chat showed answer snippet "${snippet}"` };
  }

  if (commit.updatedNote && commit.updatedNote.title?.toLowerCase() !== "inbox") {
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
    await page.getByText(/Queued in/i).last().waitFor({ timeout: 15000 });
    return { pass: true, detail: "UI kept inbox hidden and showed queued state." };
  }

  return { pass: true, detail: "No UI-specific assertion required." };
}

async function submitChatViaUi(page, message) {
  const commitResponsePromise = page.waitForResponse((response) =>
    response.url().includes("/api/v2/chat-events/commit") &&
    response.request().method() === "POST"
  , { timeout: 90000 });

  const input = page.getByTestId("chat-input");
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
      await page.screenshot({ path: failurePath, fullPage: true });
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
    await page.screenshot({ path: failurePath, fullPage: true });
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

async function runScenario(page, fixtures, scenario, index) {
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

  const routePass =
    (!scenario.expect.intent || commit.routePlan.intent === scenario.expect.intent) &&
    (!scenario.expect.strategy || commit.routePlan.strategy === scenario.expect.strategy) &&
    (!scenario.expect.targetNoteId || commit.routePlan.targetNoteId === scenario.expect.targetNoteId) &&
    (!scenario.expect.notebookId || commit.routePlan.targetNotebookId === scenario.expect.notebookId) &&
    (!scenario.expect.noteType || commit.routePlan.targetNoteType === scenario.expect.noteType) &&
    (!scenario.expect.fallbackToInbox || commit.patchPlan.fallbackToInbox === true);

  let contentPass = true;
  let contentDetail = "No content assertion required.";
  if (scenario.expect.sectionId) {
    const section = findSection(updatedNote, scenario.expect.sectionId);
    contentPass =
      Boolean(section) &&
      formatPass(section, scenario.expect.format) &&
      includesAllCaseInsensitive(section?.contentMarkdown, scenario.expect.contains) &&
      (
        !scenario.expect.containsAny?.length ||
        scenario.expect.containsAny.some((candidate) =>
          (section?.contentMarkdown ?? "").toLowerCase().includes(candidate.toLowerCase())
        )
      );
    contentDetail = section
      ? `${scenario.expect.sectionId}: ${section.contentMarkdown}`
      : `Section ${scenario.expect.sectionId} missing`;
  }

  if (scenario.expect.answerContains?.length) {
    contentPass = scenario.expect.answerContains.every((value) =>
      (commit.answer ?? "").toLowerCase().includes(value.toLowerCase())
    );
    contentDetail = commit.answer ?? "";
  }

  if (scenario.expect.answerContainsAny?.length) {
    contentPass = scenario.expect.answerContainsAny.some((value) =>
      (commit.answer ?? "").toLowerCase().includes(value.toLowerCase())
    );
    contentDetail = commit.answer ?? "";
  }

  if (scenario.expect.op) {
    const firstOp = commit.patchPlan.ops?.[0]?.op ?? null;
    contentPass = contentPass && firstOp === scenario.expect.op;
    contentDetail = `First op: ${firstOp}`;
  }

  const ui = await verifyUi(page, commit, scenario);

  const overallPass = routePass && contentPass && ui.pass;
  if (!overallPass) {
    const failurePath = path.resolve(
      scriptDir,
      `eval-real-openai-failure-${String(index + 1).padStart(2, "0")}.png`
    );
    await page.screenshot({ path: failurePath, fullPage: true });
  }

  return {
    case: scenario.name,
    message: scenario.message,
    pass: overallPass,
    routePass,
    contentPass,
    uiPass: ui.pass,
    uiDetail: ui.detail,
    contentDetail,
    routePlan: commit.routePlan,
    patchPlan: {
      fallbackToInbox: commit.patchPlan.fallbackToInbox,
      ops: commit.patchPlan.ops?.map((op) => ({
        op: op.op,
        sectionId: op.sectionId,
        title: op.title,
        contentMarkdown: op.contentMarkdown
      }))
    },
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
  const fixtures = await seedFixtures();
  const scenarios = cases(fixtures).filter((scenario) =>
    requestedCases.length === 0 || requestedCases.includes(scenario.name)
  );

  const browser = await chromium.launch({
    executablePath: browserPath(),
    headless: true
  });

  const startedAt = new Date().toISOString();
  const results = [];

  try {
    const page = await browser.newPage();
    await page.goto(frontendUrl, { waitUntil: "networkidle" });

    for (let index = 0; index < scenarios.length; index++) {
      const scenario = scenarios[index];
      try {
        results.push(await runScenario(page, fixtures, scenario, index));
      } catch (error) {
        const failurePath = path.resolve(
          scriptDir,
          `eval-real-openai-error-${String(index + 1).padStart(2, "0")}.png`
        );
        await page.screenshot({ path: failurePath, fullPage: true });
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

  const reportPath = path.resolve(scriptDir, "eval-real-openai-last-report.json");
  await fs.writeFile(reportPath, JSON.stringify(summary, null, 2));
  console.log(JSON.stringify(summary, null, 2));
}

run().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
