import { SendHorizontal } from "lucide-react";
import { motion } from "motion/react";
import { useCallback, useRef, useState } from "react";
import TextareaAutosize from "react-textarea-autosize";
import styles from "./ChatComposer.module.css";

interface ChatComposerProps {
  onSubmit: (text: string) => void;
  busy: boolean;
}

export function ChatComposer({ onSubmit, busy }: ChatComposerProps) {
  const [value, setValue] = useState("");
  const [showHint, setShowHint] = useState(true);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const handleSubmit = useCallback(() => {
    const trimmed = value.trim();
    if (!trimmed || busy) return;
    onSubmit(trimmed);
    setValue("");
  }, [value, busy, onSubmit]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        handleSubmit();
      }
    },
    [handleSubmit]
  );

  return (
    <div className={`${styles.composer} ${busy ? styles.composerBusy : ""}`}>
      <div className={styles.inputWrap}>
        <TextareaAutosize
          ref={inputRef}
          className={styles.input}
          placeholder="Send a message..."
          value={value}
          onChange={(e) => {
            setValue(e.target.value);
            setShowHint(false);
          }}
          onKeyDown={handleKeyDown}
          onFocus={() => setShowHint(true)}
          onBlur={() => setShowHint(false)}
          maxRows={5}
          disabled={busy}
        />
        {showHint && !value && (
          <span className={styles.hint}>Enter to send, Shift+Enter for newline</span>
        )}
      </div>
      <motion.button
        className={styles.sendBtn}
        onClick={handleSubmit}
        disabled={!value.trim() || busy}
        whileTap={{ scale: 0.92 }}
        aria-label="Send message"
      >
        <SendHorizontal size={16} />
      </motion.button>
    </div>
  );
}
