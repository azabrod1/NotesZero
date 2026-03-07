import { ChevronDown, ChevronUp, MessageSquare } from "lucide-react";
import { useEffect, useRef } from "react";
import type { ChatMessage as ChatMessageType, ChatMode } from "../../lib/types";
import { ChatComposer } from "./ChatComposer";
import styles from "./ChatDock.module.css";
import { ChatMessage } from "./ChatMessage";

interface ChatDockProps {
  messages: ChatMessageType[];
  mode: ChatMode;
  minimized: boolean;
  busy: boolean;
  onModeChange: (mode: ChatMode) => void;
  onSubmit: (text: string) => void;
  onToggleMinimized: () => void;
}

const MODES: { key: ChatMode; label: string }[] = [
  { key: "capture", label: "Jot" },
  { key: "query", label: "Ask" },
  { key: "edit", label: "Edit" }
];

export function ChatDock({
  messages,
  mode,
  minimized,
  busy,
  onModeChange,
  onSubmit,
  onToggleMinimized
}: ChatDockProps) {
  const threadRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (threadRef.current) {
      threadRef.current.scrollTop = threadRef.current.scrollHeight;
    }
  }, [messages]);

  return (
    <div
      className={`${styles.dock} ${minimized ? styles.dockCollapsed : styles.dockExpanded}`}
    >
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.chatLabel}>
            <MessageSquare size={14} className={styles.chatLabelIcon} />
            Chat
          </span>
          <div className={styles.modes}>
            {MODES.map((m) => (
              <button
                key={m.key}
                className={`${styles.modeChip} ${mode === m.key ? styles.modeChipActive : ""}`}
                onClick={() => onModeChange(m.key)}
              >
                {m.label}
              </button>
            ))}
          </div>
        </div>
        <button
          className={styles.toggleBtn}
          onClick={onToggleMinimized}
          aria-label={minimized ? "Expand chat" : "Minimize chat"}
        >
          {minimized ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
        </button>
      </div>

      {!minimized && (
        <>
          <div className={styles.thread} ref={threadRef}>
            {messages.map((msg) => (
              <ChatMessage key={msg.id} message={msg} />
            ))}
          </div>
          <ChatComposer mode={mode} busy={busy} onSubmit={onSubmit} />
        </>
      )}
    </div>
  );
}
