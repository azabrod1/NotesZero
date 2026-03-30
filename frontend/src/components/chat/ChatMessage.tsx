import { motion } from "motion/react";
import { useState } from "react";
import { fadeSlideUp } from "../../lib/animations";
import type { ChatMessage as ChatMessageType } from "../../lib/types";
import styles from "./ChatMessage.module.css";

interface ChatMessageProps {
  message: ChatMessageType;
  onUndo: (noteId: number, operationId: number) => void;
}

export function ChatMessage({ message, onUndo }: ChatMessageProps) {
  const [detailsOpen, setDetailsOpen] = useState(false);
  const isUser = message.role === "user";
  const hasDetails = message.commit?.diff && message.commit.diff.length > 0;
  const hasUndo = message.undo !== null && message.undo !== undefined;

  return (
    <motion.div
      className={`${styles.message} ${isUser ? styles.user : styles.assistant}`}
      {...fadeSlideUp}
    >
      <div className={styles.bubble}>
        <div className={styles.content}>{message.content}</div>

        {hasDetails && (
          <div className={styles.details}>
            <button
              className={styles.detailsToggle}
              onClick={() => setDetailsOpen((v) => !v)}
            >
              {detailsOpen ? "Hide details" : "Show details"}
            </button>
            {detailsOpen && (
              <div className={styles.diffList}>
                {message.commit!.diff.map((d) => (
                  <div key={d.sectionId} className={styles.diffItem}>
                    <span className={styles.diffLabel}>{d.label}</span>
                    <span className={styles.diffType}>{d.changeType}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {hasUndo && (
          <button
            className={styles.undoBtn}
            onClick={() => onUndo(message.undo!.noteId, message.undo!.operationId)}
          >
            Undo
          </button>
        )}
      </div>

      <span className={styles.time}>
        {new Date(message.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
      </span>
    </motion.div>
  );
}
