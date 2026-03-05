export function escapeHtml(input: string): string {
  return input
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

export function ensureHtml(input?: string | null): string {
  if (!input) {
    return "<p></p>";
  }
  const trimmed = input.trim();
  if (!trimmed) {
    return "<p></p>";
  }
  if (trimmed.startsWith("<") && trimmed.includes(">")) {
    return trimmed;
  }
  const paragraphs = trimmed
    .split(/\n{2,}/g)
    .map((chunk) => chunk.trim())
    .filter(Boolean)
    .map((chunk) => `<p>${escapeHtml(chunk).replace(/\n/g, "<br/>")}</p>`);
  return paragraphs.length > 0 ? paragraphs.join("") : "<p></p>";
}

export function stripHtml(input?: string | null): string {
  if (!input) {
    return "";
  }
  return input
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function noteTitle(rawText?: string | null): string {
  const plain = stripHtml(rawText);
  if (!plain) {
    return "Untitled page";
  }
  return plain.length > 40 ? `${plain.slice(0, 40)}...` : plain;
}
