import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildDataset, buildEditorContent, validateDataset, WORLD_COUNT } from "./reliability-lab/dataset.mjs";
import { fetchCompat } from "./reliability-lab/httpClient.mjs";
import { judgeIncorporation } from "./reliability-lab/openaiJudge.mjs";

const SCRIPT_DIR = fileURLToPath(new URL(".", import.meta.url));
const DEFAULT_OUTPUT_PATH = path.resolve(SCRIPT_DIR, "reliability-lab-last-report.json");

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const worlds = buildDataset(options.namespace);
  const validation = validateDataset(worlds);
  const selectedCaseIds = new Set(options.caseIds ?? []);

  if (options.validateOnly) {
    console.log(JSON.stringify(validation, null, 2));
    process.exit(validation.ok ? 0 : 1);
  }
  if (!validation.ok) {
    throw new Error(`Dataset validation failed: ${validation.errors.join("; ")}`);
  }

  const existingNotebooks = await request(`${options.backendUrl}/api/v2/notebooks`);
  assertNamespaceAvailable(worlds, existingNotebooks);
  assertRequestedCasesExist(worlds, selectedCaseIds);

  const state = {
    notebookIdsByKey: new Map(),
    notesByKey: new Map(),
    noteKeyById: new Map(),
    recentChatIdsByWorld: new Map()
  };

  const startedAt = new Date().toISOString();
  const results = [];
  const selectedWorlds = selectWorlds(worlds, options, selectedCaseIds);
  for (const world of selectedWorlds) {
    await seedWorld(options.backendUrl, world, state);
    for (const evalCase of selectCases(world, options, selectedCaseIds)) {
      results.push(await runCase(options, world, evalCase, state));
    }
  }

  const report = summarizeReport({
    startedAt,
    finishedAt: new Date().toISOString(),
    backendUrl: options.backendUrl,
    namespace: options.namespace,
    judgeMode: options.judgeMode,
    requestedCaseIds: options.caseIds,
    worldCount: selectedWorlds.length,
    results
  });

  if (options.comparePath) {
    const baseline = JSON.parse(await fs.readFile(options.comparePath, "utf8"));
    report.comparison = compareReports(baseline, report);
  }

  await fs.writeFile(options.outputPath, JSON.stringify(report, null, 2));
  console.log(JSON.stringify({
    outputPath: options.outputPath,
    totalCases: report.totalCases,
    passedCases: report.passedCases,
    failedCases: report.failedCases,
    topFailureBuckets: report.bucketSummary.slice(0, 5),
    comparison: report.comparison ?? null
  }, null, 2));
}

async function seedWorld(backendUrl, world, state) {
  for (const notebook of world.notebooks) {
    const created = await request(`${backendUrl}/api/v2/notebooks`, {
      method: "POST",
      body: JSON.stringify({ name: notebook.name, description: notebook.description })
    });
    state.notebookIdsByKey.set(notebook.notebookKey, created.id);
  }

  for (const note of world.notes) {
    const created = await request(`${backendUrl}/api/v2/notes`, {
      method: "POST",
      body: JSON.stringify({
        notebookId: state.notebookIdsByKey.get(note.notebookKey),
        noteType: note.noteType,
        editorContent: buildEditorContent(note)
      })
    });
    upsertStateNote(state, note.noteKey, created);
  }

  state.recentChatIdsByWorld.set(world.worldId, []);
}

async function runCase(options, world, evalCase, state) {
  const selectedNote = evalCase.selectedNoteKey ? state.notesByKey.get(evalCase.selectedNoteKey) : null;
  const beforeTarget = evalCase.expected.targetNoteKey ? deepClone(state.notesByKey.get(evalCase.expected.targetNoteKey)) : null;

  const response = await request(`${options.backendUrl}/api/v2/chat-events/commit`, {
    method: "POST",
    body: JSON.stringify({
      message: evalCase.message,
      selectedNotebookId: evalCase.selectedNotebookKey ? state.notebookIdsByKey.get(evalCase.selectedNotebookKey) : null,
      selectedNoteId: selectedNote?.id ?? null,
      currentRevisionId: selectedNote?.currentRevisionId ?? null,
      recentChatEventIds: (state.recentChatIdsByWorld.get(world.worldId) ?? []).slice(-4),
      includeDebugTrace: true
    })
  });

  appendRecentChatId(state, world.worldId, response.chatEventId);
  if (response.updatedNote) {
    upsertStateNote(state, state.noteKeyById.get(response.updatedNote.id) ?? `generated:${response.updatedNote.id}`, response.updatedNote);
  }

  const updatedText = response.updatedNote ? noteText(response.updatedNote.document) : "";
  const sectionContent = evalCase.expected.sectionId
    ? resolveSectionContent(response.updatedNote, evalCase.expected.sectionId)
    : updatedText;
  const analysis = analyzeCase(evalCase, response, sectionContent, updatedText);
  const judge = await judgeIncorporation({
    caseId: evalCase.caseId,
    category: evalCase.category,
    message: evalCase.message,
    expected: evalCase.expected,
    analysis,
    beforeNote: beforeTarget?.document ?? null,
    afterNote: response.updatedNote?.document ?? null,
    commit: response
  }, { mode: options.judgeMode });

  const scoring = scoreCase(evalCase, response, analysis, judge, state);
  return {
    schemaVersion: "EvalResultV1",
    caseId: evalCase.caseId,
    worldId: world.worldId,
    category: evalCase.category,
    message: evalCase.message,
    passed: scoring.passed,
    hardGatePassed: scoring.hardGatePassed,
    totalScore: scoring.totalScore,
    routeScore: scoring.routeScore,
    sectionFormatScore: scoring.sectionFormatScore,
    factScore: scoring.factScore,
    incorporationScore: scoring.incorporationScore,
    failureBuckets: [...new Set([...analysis.failureBuckets, ...judge.buckets, ...scoring.failureBuckets])],
    analysis,
    judge,
    routePlan: response.routePlan,
    patchPlan: response.patchPlan,
    updatedNote: response.updatedNote ? {
      id: response.updatedNote.id,
      title: response.updatedNote.title,
      notebookId: response.updatedNote.notebookId,
      currentRevisionId: response.updatedNote.currentRevisionId
    } : null,
    debugTrace: response.debugTrace ?? null
  };
}

function analyzeCase(evalCase, response, sectionContent, updatedText) {
  const mustContain = evalCase.expected.mustContain ?? [];
  const protectedTokens = evalCase.expected.protectedTokens ?? [];
  const missingMustContain = mustContain.filter((token) => !includesIgnoreCase(sectionContent ?? "", token));
  const missingProtected = protectedTokens.filter((token) => !includesIgnoreCase(updatedText, token));
  const formatPass = evalCase.expected.format ? formatMatches(sectionContent ?? "", evalCase.expected.format) : true;
  const hasDuplicateAppend = detectDuplicateAppend(sectionContent ?? "");
  const changedSectionCount = Array.isArray(response.diff) ? response.diff.length : 0;

  const failureBuckets = [];
  if (missingMustContain.length) failureBuckets.push("wrong_section");
  if (!formatPass) failureBuckets.push("wrong_format");
  if (missingProtected.length) failureBuckets.push("protected_fact_loss");
  if (hasDuplicateAppend) failureBuckets.push("duplication");
  if (evalCase.expected.rewrite !== true && response.patchPlan?.ops?.some((op) => op.op === "REPLACE_NOTE_OUTLINE")) {
    failureBuckets.push("over_rewrite");
  }

  return { sectionContent, missingMustContain, missingProtected, formatPass, hasDuplicateAppend, changedSectionCount, failureBuckets };
}

function scoreCase(evalCase, response, analysis, judge, state) {
  const failureBuckets = [];
  const expectedNotebookId = evalCase.expected.targetNotebookKey ? state.notebookIdsByKey.get(evalCase.expected.targetNotebookKey) : null;
  const expectedNote = evalCase.expected.targetNoteKey ? state.notesByKey.get(evalCase.expected.targetNoteKey) : null;
  const actualNote = response.updatedNote ?? null;

  let hardGatePassed = true;
  if (response.routePlan.intent !== evalCase.expected.intent) {
    hardGatePassed = false;
    failureBuckets.push("wrong_create_vs_update");
  }
  if (response.routePlan.strategy !== evalCase.expected.strategy) {
    hardGatePassed = false;
    failureBuckets.push(evalCase.expected.targetMode === "notebook_inbox" ? "inbox_underuse" : "inbox_overuse");
  }
  if (expectedNotebookId != null && response.routePlan.targetNotebookId !== expectedNotebookId) {
    hardGatePassed = false;
    failureBuckets.push("wrong_target");
  }
  if (evalCase.expected.targetMode === "existing_note" && expectedNote && actualNote?.id !== expectedNote.id) {
    hardGatePassed = false;
    failureBuckets.push("wrong_target");
  }
  if (evalCase.expected.targetMode === "create_visible_note" && (!actualNote || actualNote.title !== evalCase.expected.expectedTitle || actualNote.notebookId !== expectedNotebookId)) {
    hardGatePassed = false;
    failureBuckets.push("wrong_target");
  }
  if (evalCase.expected.targetMode === "notebook_inbox" && (!actualNote || actualNote.title !== "Inbox" || actualNote.notebookId !== expectedNotebookId)) {
    hardGatePassed = false;
    failureBuckets.push("wrong_create_vs_update");
  }
  if (analysis.missingProtected.length) {
    hardGatePassed = false;
    failureBuckets.push("protected_fact_loss");
  }

  let routeScore = 0;
  if (response.routePlan.intent === evalCase.expected.intent) routeScore += 10;
  if (response.routePlan.strategy === evalCase.expected.strategy) routeScore += 10;
  if (expectedNotebookId == null || response.routePlan.targetNotebookId === expectedNotebookId) routeScore += 10;
  if (
    (evalCase.expected.targetMode === "existing_note" && expectedNote && actualNote?.id === expectedNote.id)
    || (evalCase.expected.targetMode === "create_visible_note" && actualNote?.title === evalCase.expected.expectedTitle)
    || (evalCase.expected.targetMode === "notebook_inbox" && actualNote?.title === "Inbox")
  ) routeScore += 10;

  const sectionFormatScore = (analysis.missingMustContain.length ? 0 : 10) + (analysis.formatPass ? 10 : 0);
  const factScore = analysis.missingProtected.length ? 0 : 20;

  let deterministic = 10;
  if (analysis.hasDuplicateAppend) deterministic -= 3;
  if (analysis.changedSectionCount > 2 && evalCase.expected.rewrite !== true) deterministic -= 3;
  if (evalCase.expected.rewrite !== true && response.patchPlan?.ops?.some((op) => op.op === "REPLACE_NOTE_OUTLINE")) deterministic -= 4;
  if (analysis.missingMustContain.length) deterministic -= 2;
  const incorporationScore = clamp(deterministic, 0, 10) + clamp(judge.score ?? 0, 0, 10);
  const totalScore = routeScore + sectionFormatScore + factScore + incorporationScore;

  return { hardGatePassed, passed: hardGatePassed && totalScore >= 80, totalScore, routeScore, sectionFormatScore, factScore, incorporationScore, failureBuckets: [...new Set(failureBuckets)] };
}

function summarizeReport(input) {
  const bucketCounts = new Map();
  const categorySummary = new Map();
  for (const result of input.results) {
    for (const bucket of result.failureBuckets) {
      bucketCounts.set(bucket, (bucketCounts.get(bucket) ?? 0) + 1);
    }
    const categoryStats = categorySummary.get(result.category) ?? { total: 0, passed: 0 };
    categoryStats.total += 1;
    if (result.passed) categoryStats.passed += 1;
    categorySummary.set(result.category, categoryStats);
  }

  return {
    schemaVersion: "EvalResultV1",
    startedAt: input.startedAt,
    finishedAt: input.finishedAt,
    backendUrl: input.backendUrl,
    namespace: input.namespace,
    judgeMode: input.judgeMode,
    requestedCaseIds: input.requestedCaseIds ?? [],
    worldCount: input.worldCount,
    totalCases: input.results.length,
    passedCases: input.results.filter((result) => result.passed).length,
    failedCases: input.results.filter((result) => !result.passed).length,
    categorySummary: [...categorySummary.entries()].map(([category, stats]) => ({ category, total: stats.total, passed: stats.passed, failed: stats.total - stats.passed })),
    bucketSummary: [...bucketCounts.entries()].map(([bucket, count]) => ({ bucket, count })).sort((left, right) => right.count - left.count || left.bucket.localeCompare(right.bucket)),
    topFailureBuckets: [...bucketCounts.entries()].sort((left, right) => right[1] - left[1]).slice(0, 3).map(([bucket]) => bucket),
    manualReviewCandidates: [...input.results].sort((left, right) => left.totalScore - right.totalScore || left.caseId.localeCompare(right.caseId)).slice(0, 25),
    results: input.results
  };
}

function compareReports(baseline, current) {
  const baselineByCase = new Map((baseline.results ?? []).map((result) => [result.caseId, result]));
  const paired = (current.results ?? []).map((result) => [baselineByCase.get(result.caseId), result]).filter(([left]) => Boolean(left));
  const baselineWrongTarget = countBucket(paired.map(([left]) => left), "wrong_target");
  const currentWrongTarget = countBucket(paired.map(([, right]) => right), "wrong_target");
  const baselineIncorpFailures = paired.filter(([left]) => (left.incorporationScore ?? 0) < 12).length;
  const currentIncorpFailures = paired.filter(([, right]) => (right.incorporationScore ?? 0) < 12).length;
  const currentProtectedRate = passRateForBucketAbsence(paired.map(([, right]) => right), "protected_fact_loss");
  const baselineVague = categoryPassRate(paired.map(([left]) => left), "vague_inbox");
  const currentVague = categoryPassRate(paired.map(([, right]) => right), "vague_inbox");

  return {
    baselineCases: paired.length,
    wrongTargetReductionPct: percentReduction(baselineWrongTarget, currentWrongTarget),
    incorporationFailureReductionPct: percentReduction(baselineIncorpFailures, currentIncorpFailures),
    protectedFactPreservationPct: roundPct(currentProtectedRate * 100),
    vagueSafetyDeltaPctPoints: roundPct((currentVague - baselineVague) * 100),
    improvementGatePassed:
      percentReduction(baselineWrongTarget, currentWrongTarget) >= 50
      && percentReduction(baselineIncorpFailures, currentIncorpFailures) >= 40
      && currentProtectedRate >= 0.95
      && (baselineVague - currentVague) <= 0.05
  };
}

async function request(url, init = {}) {
  const response = await fetchCompat(url, { ...init, headers: { "Content-Type": "application/json", ...(init.headers ?? {}) } });
  const text = await response.text();
  if (!response.ok) throw new Error(text || `${init.method ?? "GET"} ${url} failed with ${response.status}`);
  return text ? JSON.parse(text) : null;
}

function assertNamespaceAvailable(worlds, existingNotebooks) {
  const names = new Set((existingNotebooks ?? []).map((notebook) => notebook.name));
  const collisions = worlds.flatMap((world) => world.notebooks).map((notebook) => notebook.name).filter((name) => names.has(name));
  if (collisions.length) throw new Error(`Notebook namespace already exists. Reset the DB or change EVAL_LAB_NAMESPACE. Collisions: ${collisions.slice(0, 5).join(", ")}`);
}

function assertRequestedCasesExist(worlds, selectedCaseIds) {
  if (!selectedCaseIds.size) {
    return;
  }
  const available = new Set(worlds.flatMap((world) => (world.cases ?? []).map((evalCase) => evalCase.caseId)));
  const missing = [...selectedCaseIds].filter((caseId) => !available.has(caseId));
  if (missing.length) {
    throw new Error(`Requested case ids not found: ${missing.join(", ")}`);
  }
}

function selectWorlds(worlds, options, selectedCaseIds) {
  if (!selectedCaseIds.size) {
    return worlds.slice(0, options.worldLimit);
  }
  return worlds.filter((world) => (world.cases ?? []).some((evalCase) => selectedCaseIds.has(evalCase.caseId)));
}

function selectCases(world, options, selectedCaseIds) {
  if (selectedCaseIds.size) {
    return (world.cases ?? []).filter((evalCase) => selectedCaseIds.has(evalCase.caseId));
  }
  return world.cases.slice(0, options.caseLimitPerWorld ?? world.cases.length);
}

function upsertStateNote(state, noteKey, note) {
  const stateNote = { key: noteKey, id: note.id, title: note.title, notebookId: note.notebookId, currentRevisionId: note.currentRevisionId, document: note.document };
  state.notesByKey.set(noteKey, stateNote);
  state.noteKeyById.set(note.id, noteKey);
}

function appendRecentChatId(state, worldId, chatEventId) {
  const existing = state.recentChatIdsByWorld.get(worldId) ?? [];
  existing.push(chatEventId);
  state.recentChatIdsByWorld.set(worldId, existing.slice(-4));
}

function resolveSectionContent(note, sectionId) {
  return note?.document?.sections?.find((section) => section.id === sectionId)?.contentMarkdown ?? null;
}

function noteText(document) {
  return (document?.sections ?? []).map((section) => section.contentMarkdown ?? "").join("\n");
}

function includesIgnoreCase(haystack, needle) { return (haystack ?? "").toLowerCase().includes((needle ?? "").toLowerCase()); }
function formatMatches(content, format) {
  const value = (content ?? "").trim();
  if (!format || format === "rewrite") return true;
  if (format === "checklist") return /^- \[ \] /m.test(value);
  if (format === "bullet") return /^- /m.test(value);
  if (format === "prose") return value.length > 0 && !/^- /m.test(value);
  return true;
}
function detectDuplicateAppend(content) {
  const lines = (content ?? "").split(/\r?\n/).map((line) => line.toLowerCase().replace(/^- \[ \]\s*/, "").replace(/^- /, "").trim()).filter(Boolean);
  return new Set(lines).size !== lines.length;
}
function countBucket(results, bucket) { return (results ?? []).filter((result) => (result.failureBuckets ?? []).includes(bucket)).length; }
function passRateForBucketAbsence(results, bucket) { return !results?.length ? 0 : results.filter((result) => !(result.failureBuckets ?? []).includes(bucket)).length / results.length; }
function categoryPassRate(results, category) { const filtered = (results ?? []).filter((result) => result.category === category); return !filtered.length ? 0 : filtered.filter((result) => result.passed).length / filtered.length; }
function percentReduction(baselineCount, currentCount) { return baselineCount ? roundPct(((baselineCount - currentCount) / baselineCount) * 100) : (currentCount === 0 ? 100 : 0); }
function roundPct(value) { return Math.round(value * 10) / 10; }
function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
function deepClone(value) { return value == null ? value : JSON.parse(JSON.stringify(value)); }

function parseArgs(argv) {
  const options = {
    backendUrl: process.env.EVAL_BACKEND_URL ?? "http://127.0.0.1:8080",
    namespace: process.env.EVAL_LAB_NAMESPACE ?? "lab",
    outputPath: process.env.EVAL_LAB_OUTPUT_PATH ?? DEFAULT_OUTPUT_PATH,
    comparePath: process.env.EVAL_LAB_COMPARE_TO ?? null,
    judgeMode: process.env.EVAL_JUDGE_MODE ?? "auto",
    validateOnly: false,
    worldLimit: WORLD_COUNT,
    caseLimitPerWorld: null,
    caseIds: []
  };
  for (const arg of argv) {
    if (arg === "--validate") options.validateOnly = true;
    else if (arg.startsWith("--backend-url=")) options.backendUrl = arg.slice(14);
    else if (arg.startsWith("--namespace=")) options.namespace = arg.slice(12);
    else if (arg.startsWith("--out=")) options.outputPath = path.resolve(arg.slice(6));
    else if (arg.startsWith("--compare=")) options.comparePath = path.resolve(arg.slice(10));
    else if (arg.startsWith("--judge-mode=")) options.judgeMode = arg.slice(13);
    else if (arg.startsWith("--world-limit=")) options.worldLimit = Number(arg.slice(14));
    else if (arg.startsWith("--case-limit-per-world=")) options.caseLimitPerWorld = Number(arg.slice(23));
    else if (arg.startsWith("--case-ids=")) options.caseIds = arg.slice(11).split(",").map((value) => value.trim()).filter(Boolean);
  }
  return options;
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
