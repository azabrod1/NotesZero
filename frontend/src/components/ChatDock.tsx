import { FormEvent, useMemo, useState } from "react";
import DOMPurify from "dompurify";
import { ChatMessage, ChatMode } from "../lib/types";

interface ChatDockProps {
  messages: ChatMessage[];
  mode: ChatMode;
  minimized: boolean;
  busy: boolean;
  onModeChange: (mode: ChatMode) => void;
  onSubmit: (text: string) => Promise<void>;
  onToggleMinimized: () => void;
}

const MODE_LABELS: Record<ChatMode, string> = {
  capture: "Capture",
  query: "Ask",
  edit: "Edit Page"
};

export function ChatDock({
  messages,
  mode,
  minimized,
  busy,
  onModeChange,
  onSubmit,
  onToggleMinimized
}: ChatDockProps) {
  const [draft, setDraft] = useState("");

  const title = useMemo(() => MODE_LABELS[mode], [mode]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const text = draft.trim();
    if (!text || busy) {
      return;
    }
    setDraft("");
    await onSubmit(text);
  };

  return (
    <section className={minimized ? "chat-dock is-minimized" : "chat-dock"}>
      <header className="chat-header">
        <div className="chat-title">
          <strong>Assistant</strong>
          <small>{title} mode</small>
        </div>

        <div className="chat-controls">
          {(["capture", "query", "edit"] as ChatMode[]).map((item) => (
            <button
              type="button"
              key={item}
              className={mode === item ? "mode-chip is-active" : "mode-chip"}
              onClick={() => onModeChange(item)}
            >
              {MODE_LABELS[item]}
            </button>
          ))}

          <button type="button" className="minimize-button" onClick={onToggleMinimized}>
            {minimized ? "Expand" : "Minimize"}
          </button>
        </div>
      </header>

      {!minimized && (
        <>
          <div className="chat-thread" role="log" aria-live="polite">
            {messages.map((message) => (
              <article key={message.id} className={`chat-bubble ${message.role === "assistant" ? "is-assistant" : "is-user"}`}>
                <header>
                  <strong>{message.role === "assistant" ? "Assistant" : "You"}</strong>
                  <span>{new Date(message.createdAt).toLocaleTimeString()}</span>
                </header>
                <p>{DOMPurify.sanitize(message.content ?? "")}</p>
              </article>
            ))}
          </div>

          <form className="chat-composer" onSubmit={handleSubmit}>
            <textarea
              rows={2}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="Type instruction or question..."
            />
            <button type="submit" disabled={busy}>
              {busy ? "Working..." : "Send"}
            </button>
          </form>
        </>
      )}
    </section>
  );
}
