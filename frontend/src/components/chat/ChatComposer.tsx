import { SendHorizontal } from "lucide-react";
import { useRef, useState } from "react";
import type { ChatMode } from "../../lib/types";
import styles from "./ChatComposer.module.css";

interface ChatComposerProps {
  mode: ChatMode;
  busy: boolean;
  onSubmit: (text: string) => void;
}

const PLACEHOLDER: Record<ChatMode, string> = {
  capture: "Capture a quick note...",
  query: "Ask your notebook a question...",
  edit: "Describe an edit to the current page..."
};

export function ChatComposer({ mode, busy, onSubmit }: ChatComposerProps) {
  const [text, setText] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const submit = () => {
    const trimmed = text.trim();
    if (!trimmed || busy) return;
    onSubmit(trimmed);
    setText("");
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  };

  const handleInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setText(e.target.value);
    const ta = e.target;
    ta.style.height = "auto";
    ta.style.height = Math.min(ta.scrollHeight, 120) + "px";
  };

  return (
    <div className={styles.composer}>
      <div className={styles.inputWrap}>
        <textarea
          ref={textareaRef}
          className={styles.input}
          value={text}
          onChange={handleInput}
          onKeyDown={handleKeyDown}
          placeholder={PLACEHOLDER[mode]}
          rows={1}
          disabled={busy}
        />
      </div>
      <button
        className={styles.sendBtn}
        onClick={submit}
        disabled={!text.trim() || busy}
        aria-label="Send message"
      >
        <SendHorizontal size={16} />
      </button>
    </div>
  );
}
