import { X } from "lucide-react";
import { motion } from "motion/react";
import { useEffect, useRef } from "react";
import { slideInRight } from "../../lib/animations";
import type { ChatMessage as ChatMessageType } from "../../lib/types";
import { ChatComposer } from "./ChatComposer";
import { ChatMessage } from "./ChatMessage";
import { TypingIndicator } from "./TypingIndicator";
import styles from "./ChatPanel.module.css";

interface ChatPanelProps {
  messages: ChatMessageType[];
  busy: boolean;
  onSubmit: (text: string) => void;
  onUndo: (noteId: number, operationId: number) => void;
  onClose: () => void;
  isMobile?: boolean;
}

export function ChatPanel({ messages, busy, onSubmit, onUndo, onClose, isMobile }: ChatPanelProps) {
  const threadRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (threadRef.current) {
      threadRef.current.scrollTop = threadRef.current.scrollHeight;
    }
  }, [messages.length, busy]);

  const showTyping = busy && messages.length > 0 && messages[messages.length - 1].role === "user";

  return (
    <motion.aside
      className={`${styles.panel} ${isMobile ? styles.panelMobile : ""}`}
      {...(isMobile ? {} : slideInRight)}
    >
      <div className={styles.header}>
        <span className={styles.title}>Chat</span>
        <button className={styles.closeBtn} onClick={onClose} aria-label="Close chat">
          <X size={16} />
        </button>
      </div>

      <div className={styles.thread} ref={threadRef}>
        {messages.map((msg) => (
          <ChatMessage key={msg.id} message={msg} onUndo={onUndo} />
        ))}
        {showTyping && <TypingIndicator />}
      </div>

      <ChatComposer onSubmit={onSubmit} busy={busy} />
    </motion.aside>
  );
}
