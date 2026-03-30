export const CATEGORY_COUNTS = {
  selected_note_update: 120,
  explicit_cross_note_retarget: 90,
  create_note_capture: 80,
  vague_inbox: 60,
  section_format: 80,
  protected_fact: 40,
  rewrite_correction: 30
};

export const WORLD_COUNT = 25;
export const CASES_PER_WORLD = 20;

export function buildDataset(namespace = "lab") {
  const categorySequence = buildCategorySequence();
  const categoryOccurrences = Object.fromEntries(Object.keys(CATEGORY_COUNTS).map((key) => [key, 0]));
  const worlds = [];

  for (let worldIndex = 0; worldIndex < WORLD_COUNT; worldIndex += 1) {
    const variant = buildWorldVariant(worldIndex, namespace);
    const cases = [];

    for (let slot = 0; slot < CASES_PER_WORLD; slot += 1) {
      const category = categorySequence[(worldIndex * CASES_PER_WORLD) + slot];
      const occurrence = categoryOccurrences[category];
      categoryOccurrences[category] += 1;
      const template = CATEGORY_TEMPLATES[category][occurrence % CATEGORY_TEMPLATES[category].length];
      cases.push(template({ variant, worldIndex, slot, occurrence }));
    }

    worlds.push({
      schemaVersion: "EvalWorldV1",
      worldId: variant.worldId,
      title: variant.title,
      namespace,
      notebooks: Object.values(variant.notebooks),
      notes: Object.values(variant.notes),
      cases
    });
  }

  return worlds;
}

export function validateDataset(worlds) {
  const errors = [];
  const caseIds = new Set();
  const counts = Object.fromEntries(Object.keys(CATEGORY_COUNTS).map((key) => [key, 0]));

  if (worlds.length !== WORLD_COUNT) {
    errors.push(`expected ${WORLD_COUNT} worlds but found ${worlds.length}`);
  }

  for (const world of worlds) {
    if ((world.cases ?? []).length !== CASES_PER_WORLD) {
      errors.push(`${world.worldId} does not have ${CASES_PER_WORLD} cases`);
    }
    for (const evalCase of world.cases ?? []) {
      if (caseIds.has(evalCase.caseId)) {
        errors.push(`duplicate case id ${evalCase.caseId}`);
      }
      caseIds.add(evalCase.caseId);
      counts[evalCase.category] = (counts[evalCase.category] ?? 0) + 1;
    }
  }

  for (const [category, expectedCount] of Object.entries(CATEGORY_COUNTS)) {
    if (counts[category] !== expectedCount) {
      errors.push(`category ${category} expected ${expectedCount} but found ${counts[category]}`);
    }
  }

  return {
    ok: errors.length === 0,
    worldCount: worlds.length,
    totalCases: caseIds.size,
    categoryCounts: counts,
    errors
  };
}

export function buildEditorContent(note) {
  if (note.noteType === "project_note/v1") {
    return projectEditorJson(note.title, {
      summary: note.summary,
      status: note.status,
      decisions: note.decisions,
      tasks: note.tasks,
      openQuestions: note.openQuestions,
      timeline: note.timeline,
      references: note.references
    });
  }
  return genericEditorJson(note.title, {
    summary: note.summary,
    body: note.body,
    actionItems: note.actionItems,
    references: note.references
  });
}

function buildCategorySequence() {
  const remaining = { ...CATEGORY_COUNTS };
  const sequence = [];

  while (sequence.length < WORLD_COUNT * CASES_PER_WORLD) {
    const previous = sequence.length === 0 ? null : sequence[sequence.length - 1];
    const category = Object.entries(remaining)
      .filter(([, count]) => count > 0)
      .sort((left, right) => {
        const leftScore = left[1] - (left[0] === previous ? 0.35 : 0);
        const rightScore = right[1] - (right[0] === previous ? 0.35 : 0);
        if (rightScore !== leftScore) {
          return rightScore - leftScore;
        }
        return left[0].localeCompare(right[0]);
      })[0][0];
    sequence.push(category);
    remaining[category] -= 1;
  }

  return sequence;
}

function buildWorldVariant(worldIndex, namespace) {
  const seedId = `W${String(worldIndex + 1).padStart(2, "0")}`;
  const suffix = `${namespace} ${seedId}`;
  const [modelA, modelB] = [
    ["gpt-5-mini-2025-08-07", "gpt-5.4-2026-03-05"],
    ["gpt-5-mini-2025-08-07", "gpt-5.4-2026-03-12"],
    ["gpt-5.4-mini-2026-03-05", "gpt-5.4-2026-03-05"]
  ][worldIndex % 3];
  const values = {
    seedId,
    suffix,
    modelA,
    modelB,
    dogDose: `${5 + worldIndex}mg`,
    fever: `${38 + (worldIndex % 2)}.${(worldIndex % 4) + 2}C`,
    quote: [
      "preserve exact client phrasing",
      "keep mock AI in unit tests",
      "quoted phrases must survive rewrites",
      "preserve exact measurements"
    ][worldIndex % 4],
    docsUrl: `https://docs.noteszero.dev/evals/${seedId.toLowerCase()}`,
    command: `./deploy --env staging-${seedId.toLowerCase()} --dry-run`,
    latencyTrackingTitle: `Latency Tracking ${suffix}`,
    boardingChecklistTitle: `Boarding Checklist ${suffix}`,
    allergyObservationsTitle: `Allergy Observations ${suffix}`,
    promptFailureTitle: `Prompt Failure Review ${suffix}`,
    entFollowupTitle: `ENT Follow-up ${suffix}`
  };

  const notebooks = {
    product_ops: notebookSeed("product_ops", `Product Ops ${suffix}`, "Launches, deploys, and operational notes."),
    product_planning: notebookSeed("product_planning", `Product Planning ${suffix}`, "Experiments, roadmaps, and planning notes."),
    dog_care: notebookSeed("dog_care", `Dog Care ${suffix}`, "Dog health, gear, and training notes."),
    family_health: notebookSeed("family_health", `Family Health ${suffix}`, "Sleep, symptoms, and family follow-up notes.")
  };

  const notes = {
    work_launch: projectNoteSeed("work_launch", "product_ops", `Launch Control ${suffix}`, {
      summary: "Launch tracker for the AI-native notes product.",
      status: "Local v2 write flow is stable in development.",
      decisions: "- Keep mock AI as the default in unit tests.",
      tasks: "- [ ] Add a broader reliability lab.",
      openQuestions: "- When should routing escalate to a stronger model?",
      timeline: "- 2026-03-10 Seeded the launch tracker.",
      references: `- ${values.docsUrl}`
    }),
    work_eval: projectNoteSeed("work_eval", "product_planning", `Eval Stream ${suffix}`, {
      summary: "Working note for model eval and failure analysis.",
      status: "Collecting wrong-target and over-rewrite failures.",
      decisions: `- ${values.quote}.`,
      tasks: "- [ ] Capture route confidence in reports.",
      openQuestions: "- Should notebook inbox fallback stay conservative?",
      timeline: "- 2026-03-12 Started expanded eval planning.",
      references: "- https://platform.openai.com/docs/guides/structured-outputs"
    }),
    work_runbook: genericNoteSeed("work_runbook", "product_ops", `Deploy Runbook ${suffix}`, {
      summary: "Deployment and rollback steps for NotesZero.",
      body: "Deploy order: backend build, migrations, frontend release. Rollback uses the previous green build.",
      actionItems: "- [ ] Verify rollback health checks.",
      references: `- ${values.docsUrl}/runbook`
    }),
    work_integrations: genericNoteSeed("work_integrations", "product_planning", `API Integrations ${suffix}`, {
      summary: "External model integrations and prompt notes.",
      body: `Compare ${modelA} and ${modelB}. Keep vendor docs and exact model ids here.`,
      actionItems: "- [ ] Review prompt regressions.",
      references: `- ${values.docsUrl}/integrations`
    }),
    work_growth: projectNoteSeed("work_growth", "product_planning", `Growth Experiments ${suffix}`, {
      summary: "Activation and onboarding experiment plan.",
      status: "Preparing the first onboarding cohort.",
      decisions: "- Focus on activation within the first session.",
      tasks: "- [ ] Draft experiment brief.",
      openQuestions: "- Which activation metric should gate rollout?",
      timeline: "- 2026-03-11 Defined the initial hypothesis.",
      references: `- ${values.docsUrl}/growth`
    }),
    dog_medication: genericNoteSeed("dog_medication", "dog_care", `Dog Medication Log ${suffix}`, {
      summary: "Track doses, timing, and reactions for the dog.",
      body: `Track breakfast dose, evening dose, appetite, and behavior. Last confirmed dose was ${values.dogDose}.`,
      actionItems: "- [ ] Refill trazodone before travel.",
      references: `- ${values.docsUrl}/dog-medication`
    }),
    dog_training: projectNoteSeed("dog_training", "dog_care", `Dog Training Plan ${suffix}`, {
      summary: "Calm greetings and leash manners plan.",
      status: "Shorter outdoor sessions are helping.",
      decisions: "- Keep sessions under 10 minutes.",
      tasks: "- [ ] Practice calm greetings.",
      openQuestions: "- How should we reward quiet check-ins?",
      timeline: "- 2026-03-14 Started shorter outdoor sessions.",
      references: `- ${values.docsUrl}/dog-training`
    }),
    dog_gear: genericNoteSeed("dog_gear", "dog_care", `Dog Gear Wishlist ${suffix}`, {
      summary: "Rain gear, travel gear, and walking extras.",
      body: "Track harnesses, travel bowls, rain coats, and backup leashes.",
      actionItems: "- [ ] Compare travel bowls.",
      references: `- ${values.docsUrl}/dog-gear`
    }),
    family_sleep: genericNoteSeed("family_sleep", "family_health", `Sleep Notes ${suffix}`, {
      summary: "Sleep observations and bedtime routine notes.",
      body: "Track bedtime, naps, wakeups, and possible triggers for early waking.",
      actionItems: "- [ ] Record wakeups for three nights.",
      references: `- ${values.docsUrl}/family-sleep`
    }),
    family_followup: projectNoteSeed("family_followup", "family_health", `Pediatrician Follow-up ${suffix}`, {
      summary: "Questions and follow-ups for the next appointment.",
      status: "Collecting questions before the appointment.",
      decisions: "- Bring the symptom timeline.",
      tasks: "- [ ] Pack symptom notes.",
      openQuestions: "- Should nap timing change during recovery?",
      timeline: "- 2026-03-13 Started collecting follow-up questions.",
      references: `- ${values.docsUrl}/family-followup`
    }),
    family_symptom: genericNoteSeed("family_symptom", "family_health", `Family Symptom Log ${suffix}`, {
      summary: "Temperature and symptom tracking for the family.",
      body: `Track temperature readings, cough, medicine timing, and energy. Latest tracked fever was ${values.fever}.`,
      actionItems: "- [ ] Bring the symptom timeline video.",
      references: `- ${values.docsUrl}/family-symptoms`
    })
  };

  return {
    worldId: `world-${String(worldIndex + 1).padStart(2, "0")}`,
    title: `Reliability World ${seedId}`,
    values,
    notebooks,
    notes
  };
}

function notebookSeed(notebookKey, name, description) {
  return { notebookKey, name, description };
}

function projectNoteSeed(noteKey, notebookKey, title, sections) {
  return { noteKey, notebookKey, noteType: "project_note/v1", title, ...sections };
}

function genericNoteSeed(noteKey, notebookKey, title, sections) {
  return { noteKey, notebookKey, noteType: "generic_note/v1", title, ...sections };
}

function caseDef(category, ctx, input) {
  return {
    schemaVersion: "EvalCaseV1",
    caseId: `${ctx.variant.worldId}-${String(ctx.slot + 1).padStart(2, "0")}-${category}-${String(ctx.occurrence + 1).padStart(3, "0")}`,
    worldId: ctx.variant.worldId,
    category,
    selectedNotebookKey: input.selectedNotebookKey ?? null,
    selectedNoteKey: input.selectedNoteKey ?? null,
    message: input.message,
    expected: input.expected
  };
}

const CATEGORY_TEMPLATES = {
  selected_note_update: [
    (ctx) => selectedCase("selected_note_update", ctx, "product_ops", "work_launch", `Add a task to compare ${ctx.variant.values.modelA} and ${ctx.variant.values.modelB} in evals.`, "product_ops", "work_launch", "tasks", "checklist", [ctx.variant.values.modelA, ctx.variant.values.modelB]),
    (ctx) => selectedCase("selected_note_update", ctx, "product_planning", "work_eval", "Status update: local real-model smoke tests are stable.", "product_planning", "work_eval", "status", "prose", ["real-model smoke tests are stable"]),
    (ctx) => selectedCase("selected_note_update", ctx, "product_ops", "work_runbook", "Add a note to verify Flyway migration order before frontend deploy.", "product_ops", "work_runbook", "body", "prose", ["Flyway migration order", "frontend deploy"]),
    (ctx) => selectedCase("selected_note_update", ctx, "dog_care", "dog_medication", `Add that appetite was low before the 7pm dose and the dog got ${ctx.variant.values.dogDose}.`, "dog_care", "dog_medication", "body", "prose", ["appetite was low", "7pm", ctx.variant.values.dogDose], ["7pm", ctx.variant.values.dogDose]),
    (ctx) => selectedCase("selected_note_update", ctx, "dog_care", "dog_training", "Decision: stop using the retractable leash.", "dog_care", "dog_training", "decisions", "bullet", ["retractable leash"]),
    (ctx) => selectedCase("selected_note_update", ctx, "family_health", "family_followup", "Open question: should nap timing shift during recovery?", "family_health", "family_followup", "open_questions", "bullet", ["nap timing", "recovery"])
  ],
  explicit_cross_note_retarget: [
    (ctx) => crossCase("explicit_cross_note_retarget", ctx, "dog_care", "dog_training", "work_runbook", "product_ops", "work_runbook", "body", "prose", `In ${ctx.variant.notes.work_runbook.title}, add a note to verify Flyway migration order before frontend deploy.`, ["Flyway migration order", "frontend deploy"]),
    (ctx) => crossCase("explicit_cross_note_retarget", ctx, "product_ops", "work_launch", "dog_medication", "dog_care", "dog_medication", "body", "prose", `In ${ctx.variant.notes.dog_medication.title}, add that appetite was low before the evening dose.`, ["appetite was low", "evening dose"]),
    (ctx) => crossCase("explicit_cross_note_retarget", ctx, "family_health", "family_sleep", "work_eval", "product_planning", "work_eval", "tasks", "checklist", `In ${ctx.variant.notes.work_eval.title}, add a task to capture token cost per commit.`, ["token cost per commit"]),
    (ctx) => crossCase("explicit_cross_note_retarget", ctx, "product_ops", "work_runbook", "family_symptom", "family_health", "family_symptom", "body", "prose", `In ${ctx.variant.notes.family_symptom.title}, add that fever peaked at ${ctx.variant.values.fever} around 7pm.`, [ctx.variant.values.fever, "7pm"], [ctx.variant.values.fever, "7pm"]),
    (ctx) => crossCase("explicit_cross_note_retarget", ctx, "dog_care", "dog_gear", "family_sleep", "family_health", "family_sleep", "body", "bullet", `In ${ctx.variant.notes.family_sleep.title}, add a bullet list for bedtime routine issues: late nap, long bath, bright room.`, ["late nap", "long bath", "bright room"])
  ],
  create_note_capture: [
    (ctx) => createCase("create_note_capture", ctx, "product_planning", `Create a project note called ${ctx.variant.values.latencyTrackingTitle}. Add a summary that we should measure wrong-target writes and a task to log route confidence.`, "project_note/v1", ctx.variant.values.latencyTrackingTitle, "tasks", "checklist", ["log route confidence"]),
    (ctx) => createCase("create_note_capture", ctx, "dog_care", `Create a project note called ${ctx.variant.values.boardingChecklistTitle} with food, meds, and emergency contacts. Add a task to pack food.`, "project_note/v1", ctx.variant.values.boardingChecklistTitle, "tasks", "checklist", ["pack food"]),
    (ctx) => createCase("create_note_capture", ctx, "family_health", `Create a note called ${ctx.variant.values.allergyObservationsTitle}. Mention early waking and a mild rash in the body.`, "generic_note/v1", ctx.variant.values.allergyObservationsTitle, "body", "prose", ["early waking", "mild rash"]),
    (ctx) => createCase("create_note_capture", ctx, "product_ops", `Create a note called ${ctx.variant.values.promptFailureTitle}. Add a summary that this note tracks wrong-target writes and over-rewrites.`, "generic_note/v1", ctx.variant.values.promptFailureTitle, "summary", "prose", ["wrong-target writes", "over-rewrites"]),
    (ctx) => createCase("create_note_capture", ctx, "family_health", `Create a project note called ${ctx.variant.values.entFollowupTitle}. Add a task to bring the symptom timeline video.`, "project_note/v1", ctx.variant.values.entFollowupTitle, "tasks", "checklist", ["symptom timeline video"])
  ],
  vague_inbox: [
    (ctx) => inboxCase("vague_inbox", ctx, "dog_care", "Need to remember this at some point."),
    (ctx) => inboxCase("vague_inbox", ctx, "family_health", "Don't forget this later."),
    (ctx) => inboxCase("vague_inbox", ctx, "product_planning", "Remember this for sometime next week."),
    (ctx) => inboxCase("vague_inbox", ctx, "product_ops", "Need to stash this somewhere for later.")
  ],
  section_format: [
    (ctx) => selectedCase("section_format", ctx, "product_ops", "work_launch", "Decision: preserve quoted phrases and exact measurements.", "product_ops", "work_launch", "decisions", "bullet", ["quoted phrases", "exact measurements"]),
    (ctx) => selectedCase("section_format", ctx, "product_planning", "work_growth", "Add a timeline event for 2026-03-15: tuned create-note guardrails.", "product_planning", "work_growth", "timeline", "bullet", ["2026-03-15", "create-note guardrails"], ["2026-03-15"]),
    (ctx) => selectedCase("section_format", ctx, "family_health", "family_sleep", "In the body, add a bullet list for bedtime routine issues: late nap, long bath, bright room.", "family_health", "family_sleep", "body", "bullet", ["late nap", "long bath", "bright room"]),
    (ctx) => selectedCase("section_format", ctx, "product_planning", "work_integrations", "Save this docs link: https://platform.openai.com/docs/guides/structured-outputs", "product_planning", "work_integrations", "references", "bullet", ["https://platform.openai.com/docs/guides/structured-outputs"], ["https://platform.openai.com/docs/guides/structured-outputs"]),
    (ctx) => selectedCase("section_format", ctx, "family_health", "family_followup", "Add a task to bring the symptom timeline video.", "family_health", "family_followup", "tasks", "checklist", ["symptom timeline video"])
  ],
  protected_fact: [
    (ctx) => selectedCase("protected_fact", ctx, "dog_care", "dog_medication", `Add that appetite was low before the 7pm dose and the dog got ${ctx.variant.values.dogDose} of trazodone.`, "dog_care", "dog_medication", "body", "prose", ["7pm", ctx.variant.values.dogDose, "trazodone"], ["7pm", ctx.variant.values.dogDose]),
    (ctx) => selectedCase("protected_fact", ctx, "family_health", "family_symptom", `Update this note: fever peaked at ${ctx.variant.values.fever} around 7pm and keep the phrase "${ctx.variant.values.quote}".`, "family_health", "family_symptom", "body", "prose", [ctx.variant.values.fever, "7pm", ctx.variant.values.quote], [ctx.variant.values.fever, "7pm", ctx.variant.values.quote]),
    (ctx) => selectedCase("protected_fact", ctx, "product_planning", "work_integrations", `Add a note to compare ${ctx.variant.values.modelA} with ${ctx.variant.values.modelB} and save ${ctx.variant.values.docsUrl}.`, "product_planning", "work_integrations", "body", "prose", [ctx.variant.values.modelA, ctx.variant.values.modelB, ctx.variant.values.docsUrl], [ctx.variant.values.modelA, ctx.variant.values.modelB, ctx.variant.values.docsUrl]),
    (ctx) => selectedCase("protected_fact", ctx, "product_ops", "work_runbook", `Save the exact command: ${ctx.variant.values.command}.`, "product_ops", "work_runbook", "body", "prose", [ctx.variant.values.command], [ctx.variant.values.command])
  ],
  rewrite_correction: [
    (ctx) => rewriteCase("rewrite_correction", ctx, "product_ops", "work_runbook", "Rewrite this note into a cleaner summary with next steps. Preserve that it is about deploy order and rollback.", "product_ops", "work_runbook", ["deploy order", "rollback"]),
    (ctx) => rewriteCase("rewrite_correction", ctx, "family_health", "family_symptom", "Rewrite this note as a cleaner summary with next steps. Preserve the fever tracking context.", "family_health", "family_symptom", ["fever", "next steps"]),
    (ctx) => selectedCase("rewrite_correction", ctx, "dog_care", "dog_training", "Replace the status with a cleaner note that calmer greetings are improving after shorter outdoor sessions.", "dog_care", "dog_training", "status", "prose", ["calmer greetings", "shorter outdoor sessions"])
  ]
};

function selectedCase(category, ctx, selectedNotebookKey, selectedNoteKey, message, targetNotebookKey, targetNoteKey, sectionId, format, mustContain, protectedTokens = []) {
  return caseDef(category, ctx, {
    selectedNotebookKey,
    selectedNoteKey,
    message,
    expected: {
      intent: "WRITE_EXISTING_NOTE",
      strategy: "DIRECT_APPLY",
      targetMode: "existing_note",
      targetNotebookKey,
      targetNoteKey,
      sectionId,
      format,
      mustContain,
      protectedTokens
    }
  });
}

function crossCase(category, ctx, selectedNotebookKey, selectedNoteKey, _explicitTargetKey, targetNotebookKey, targetNoteKey, sectionId, format, message, mustContain, protectedTokens = []) {
  return selectedCase(category, ctx, selectedNotebookKey, selectedNoteKey, message, targetNotebookKey, targetNoteKey, sectionId, format, mustContain, protectedTokens);
}

function createCase(category, ctx, selectedNotebookKey, message, targetNoteType, expectedTitle, sectionId, format, mustContain) {
  return caseDef(category, ctx, {
    selectedNotebookKey,
    message,
    expected: {
      intent: "CREATE_NOTE",
      strategy: "DIRECT_APPLY",
      targetMode: "create_visible_note",
      targetNotebookKey: selectedNotebookKey,
      targetNoteType,
      expectedTitle,
      sectionId,
      format,
      mustContain,
      protectedTokens: []
    }
  });
}

function inboxCase(category, ctx, selectedNotebookKey, message) {
  return caseDef(category, ctx, {
    selectedNotebookKey,
    message,
    expected: {
      intent: "CREATE_NOTE",
      strategy: "NOTEBOOK_INBOX",
      targetMode: "notebook_inbox",
      targetNotebookKey: selectedNotebookKey,
      sectionId: "inbox",
      format: "bullet",
      mustContain: [message],
      protectedTokens: []
    }
  });
}

function rewriteCase(category, ctx, selectedNotebookKey, selectedNoteKey, message, targetNotebookKey, targetNoteKey, mustContain) {
  return caseDef(category, ctx, {
    selectedNotebookKey,
    selectedNoteKey,
    message,
    expected: {
      intent: "WRITE_EXISTING_NOTE",
      strategy: "DIRECT_APPLY",
      targetMode: "existing_note",
      targetNotebookKey,
      targetNoteKey,
      format: "rewrite",
      rewrite: true,
      mustContain,
      protectedTokens: []
    }
  });
}

function projectEditorJson(title, values) {
  return JSON.stringify([
    heading(1, title),
    heading(2, "Summary"), paragraph(values.summary ?? ""),
    heading(2, "Status"), paragraph(values.status ?? ""),
    heading(2, "Decisions"), paragraph(values.decisions ?? ""),
    heading(2, "Tasks"), paragraph(values.tasks ?? ""),
    heading(2, "Open Questions"), paragraph(values.openQuestions ?? ""),
    heading(2, "Timeline"), paragraph(values.timeline ?? ""),
    heading(2, "References"), paragraph(values.references ?? ""),
    heading(2, "Inbox"), paragraph("")
  ]);
}

function genericEditorJson(title, values) {
  return JSON.stringify([
    heading(1, title),
    heading(2, "Summary"), paragraph(values.summary ?? ""),
    heading(2, "Body"), paragraph(values.body ?? ""),
    heading(2, "Action Items"), paragraph(values.actionItems ?? ""),
    heading(2, "References"), paragraph(values.references ?? ""),
    heading(2, "Inbox"), paragraph("")
  ]);
}

function heading(level, text) {
  return { type: "heading", props: { level }, content: [{ type: "text", text, styles: {} }], children: [] };
}

function paragraph(text) {
  return { type: "paragraph", content: text ? [{ type: "text", text, styles: {} }] : [], children: [] };
}
