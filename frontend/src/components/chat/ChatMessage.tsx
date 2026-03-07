import DOMPurify from "dompurify";
import type { ChatMessage as ChatMessageType } from "../../lib/types";
import styles from "./ChatMessage.module.css";

interface ChatMessageProps {
  message: ChatMessageType;
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit"
    });
  } catch {
    return "";
  }
}

export function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === "user";
  const sanitized = DOMPurify.sanitize(message.content);

  return (
    <div className={`${styles.message} ${isUser ? styles.user : styles.assistant}`}>
      <div className={styles.bubble}>
        <div
          className={styles.bubbleContent}
          dangerouslySetInnerHTML={{ __html: sanitized }}
        />
      </div>
      <div className={styles.meta}>
        <span className={styles.time}>{formatTime(message.createdAt)}</span>
      </div>
    </div>
  );
}
