import { fetchCompat } from "./httpClient.mjs";

const DEFAULT_BASE_URL = process.env.OPENAI_BASE_URL ?? "https://api.openai.com/v1";
const DEFAULT_MODEL = process.env.EVAL_JUDGE_MODEL ?? "gpt-5-mini-2025-08-07";

export async function judgeIncorporation(input, options = {}) {
  const mode = options.mode ?? process.env.EVAL_JUDGE_MODE ?? "auto";
  const apiKey = options.apiKey ?? process.env.EVAL_JUDGE_OPENAI_API_KEY ?? process.env.OPENAI_API_KEY ?? "";

  if (mode === "off") {
    return heuristicJudge(input);
  }
  if (!apiKey) {
    if (mode === "required") {
      throw new Error("Judge mode is required but no OpenAI API key is configured.");
    }
    return heuristicJudge(input);
  }

  try {
    return await openAiJudge(input, {
      apiKey,
      model: options.model ?? DEFAULT_MODEL,
      baseUrl: options.baseUrl ?? DEFAULT_BASE_URL
    });
  } catch (error) {
    if (mode === "required") {
      throw error;
    }
    return {
      ...heuristicJudge(input),
      rationale: `Fell back to heuristic judge: ${error.message}`
    };
  }
}

export function heuristicJudge(input) {
  const buckets = new Set();
  let score = 10;

  if (input.expected?.rewrite !== true && input.commit?.patchPlan?.ops?.some((op) => op.op === "REPLACE_NOTE_OUTLINE")) {
    buckets.add("over_rewrite");
    score -= 4;
  }
  if (input.analysis?.hasDuplicateAppend) {
    buckets.add("duplication");
    score -= 3;
  }
  if (input.analysis?.missingMustContain?.length) {
    buckets.add("wrong_section");
    score -= 2;
  }
  if (input.analysis?.formatPass === false) {
    buckets.add("wrong_format");
    score -= 2;
  }
  if (input.analysis?.protectedTokensPass === false) {
    buckets.add("protected_fact_loss");
    score -= 4;
  }
  if (input.analysis?.changedSectionCount > 2 && input.expected?.rewrite !== true) {
    buckets.add("over_rewrite");
    score -= 2;
  }

  return {
    score: clamp(score, 0, 10),
    buckets: [...buckets],
    rationale: buckets.size === 0
      ? "Heuristic judge saw a focused edit that preserved the required specifics."
      : `Heuristic judge flagged: ${[...buckets].join(", ")}.`
  };
}

async function openAiJudge(input, options) {
  const requestBody = {
    model: options.model,
    store: false,
    reasoning: { effort: "minimal" },
    instructions: judgeInstructions(),
    max_output_tokens: 500,
    input: [
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: judgeInput(input)
          }
        ]
      }
    ],
    text: {
      format: {
        type: "json_schema",
        name: "noteszero_eval_judge_v1",
        strict: true,
        schema: judgeSchema()
      }
    }
  };

  const response = await fetchCompat(normalizeBaseUrl(options.baseUrl) + "/responses", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${options.apiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(requestBody)
  });

  const payloadText = await response.text();
  if (!response.ok) {
    throw new Error(payloadText || `Judge request failed with ${response.status}`);
  }
  const payload = JSON.parse(payloadText);
  const outputText = extractOutputText(payload);
  if (!outputText) {
    throw new Error("Judge response did not contain structured output.");
  }
  return JSON.parse(extractJsonObject(stripCodeFence(outputText)) ?? outputText);
}

function judgeInstructions() {
  return `
You are NotesZeroEvalJudge.

Task:
Judge whether the updated note incorporated the user's addition cleanly and safely.

Rules:
- Return JSON only that matches the schema.
- Score from 0 to 10.
- Penalize wrong section placement, awkward duplication, contradictions, over-rewrites, and loss of exact facts.
- Respect the expected target and expected section.
- Buckets must be chosen only from: wrong_section, wrong_format, protected_fact_loss, duplication, contradiction, over_rewrite, inbox_overuse, inbox_underuse.
- Keep rationale short and concrete.
`.trim();
}

function judgeInput(input) {
  return `
CASE ID
${input.caseId}

CATEGORY
${input.category}

MESSAGE
${input.message}

EXPECTED
${JSON.stringify(input.expected, null, 2)}

DETERMINISTIC ANALYSIS
${JSON.stringify(input.analysis, null, 2)}

BEFORE NOTE
${JSON.stringify(input.beforeNote ?? null, null, 2)}

AFTER NOTE
${JSON.stringify(input.afterNote ?? null, null, 2)}

PATCH PLAN
${JSON.stringify(input.commit?.patchPlan ?? null, null, 2)}
`.trim();
}

function judgeSchema() {
  return {
    type: "object",
    additionalProperties: false,
    properties: {
      score: { type: "integer", minimum: 0, maximum: 10 },
      buckets: {
        type: "array",
        items: {
          type: "string",
          enum: [
            "wrong_section",
            "wrong_format",
            "protected_fact_loss",
            "duplication",
            "contradiction",
            "over_rewrite",
            "inbox_overuse",
            "inbox_underuse"
          ]
        }
      },
      rationale: { type: "string" }
    },
    required: ["score", "buckets", "rationale"]
  };
}

function extractOutputText(payload) {
  for (const output of payload.output ?? []) {
    for (const content of output.content ?? []) {
      if (typeof content.text === "string" && content.text.trim()) {
        return content.text;
      }
    }
  }
  return typeof payload.output_text === "string" ? payload.output_text : null;
}

function stripCodeFence(value) {
  if (!value) {
    return value;
  }
  return value
    .trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "")
    .trim();
}

function extractJsonObject(value) {
  if (!value) {
    return null;
  }
  const start = value.indexOf("{");
  const end = value.lastIndexOf("}");
  if (start < 0 || end <= start) {
    return null;
  }
  return value.slice(start, end + 1);
}

function normalizeBaseUrl(baseUrl) {
  return baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}
