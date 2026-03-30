import { ChevronDown, ChevronUp, MessageSquare } from "lucide-react";
import { useEffect, useRef } from "react";
import type { ChatMessage as ChatMessageType } from "../../lib/types";
import { ChatComposer } from "./ChatComposer";
import styles from "./ChatDock.module.css";
import { ChatMessage } from "./ChatMessage";

interface ChatDockProps {
  messages: ChatMessageType[];
  minimized: boolean;
  busy: boolean;
  onSubmit: (text: string) => void;
  onUndo: (noteId: number, operationId: number) => void;
  onToggleMinimized: () => void;
}

export function ChatDock({
  messages,
  minimized,
  busy,
  onSubmit,
  onUndo,
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
              <ChatMessage key={msg.id} message={msg} onUndo={onUndo} />
            ))}
          </div>
          <ChatComposer busy={busy} onSubmit={onSubmit} />
        </>
      )}
    </div>
  );
}
