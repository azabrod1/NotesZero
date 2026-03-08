export function escapeHtml(input: string): string {
  return input
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
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

/**
 * Extract plain text from BlockNote JSON string.
 * Falls back to stripHtml for legacy HTML content.
 */
export function extractPlainText(rawText?: string | null): string {
  if (!rawText) return "";
  const trimmed = rawText.trim();
  if (trimmed.startsWith("[")) {
    try {
      const blocks = JSON.parse(trimmed);
      return blocksToPlainText(blocks);
    } catch {
      return "";
    }
  }
  // Legacy HTML
  return stripHtml(trimmed);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function blocksToPlainText(blocks: any[]): string {
  return blocks
    .map((block) => {
      let text = "";
      if (block.content && Array.isArray(block.content)) {
        text = block.content
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          .map((inline: any) => inline.text ?? "")
          .join("");
      }
      if (block.children && Array.isArray(block.children)) {
        const childText = blocksToPlainText(block.children);
        if (childText) text += " " + childText;
      }
      return text;
    })
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Check if BlockNote JSON content is empty.
 */
export function isBlockNoteEmpty(rawText?: string | null): boolean {
  const plain = extractPlainText(rawText);
  return !plain.trim();
}

export function noteTitle(rawText?: string | null): string {
  const plain = extractPlainText(rawText);
  if (!plain) {
    return "Untitled page";
  }
  return plain.length > 40 ? `${plain.slice(0, 40)}...` : plain;
}
