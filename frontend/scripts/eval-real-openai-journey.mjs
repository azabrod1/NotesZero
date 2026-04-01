if (process.env.EVAL_SUITE == null || process.env.EVAL_SUITE === "") {
  process.env.EVAL_SUITE = "journey";
}

await import("./eval-real-openai.mjs");
