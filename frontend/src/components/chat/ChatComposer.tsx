import { SendHorizontal } from "lucide-react";
import { useRef, useState } from "react";
import styles from "./ChatComposer.module.css";

interface ChatComposerProps {
  busy: boolean;
  onSubmit: (text: string) => void;
}

export function ChatComposer({ busy, onSubmit }: ChatComposerProps) {
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
          placeholder="Message NotesZero to write, route, or answer from your notes..."
          rows={1}
          disabled={busy}
          data-testid="chat-input"
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
