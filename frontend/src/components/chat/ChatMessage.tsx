import type { ChatMessage as ChatMessageType } from "../../lib/types";
import styles from "./ChatMessage.module.css";

interface ChatMessageProps {
  message: ChatMessageType;
  onUndo: (noteId: number, operationId: number) => void;
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

function isSystemInboxTitle(title?: string | null): boolean {
  return (title ?? "").trim().toLowerCase() === "inbox";
}

export function ChatMessage({ message, onUndo }: ChatMessageProps) {
  const isUser = message.role === "user";
  const queuedForLater = Boolean(message.commit?.patchPlan.fallbackToInbox);
  const queuedInHiddenInbox = Boolean(
    queuedForLater && isSystemInboxTitle(message.commit?.updatedNote?.title)
  );

  return (
    <div className={`${styles.message} ${isUser ? styles.user : styles.assistant}`}>
      <div className={styles.bubble}>
        <div className={styles.bubbleContent}>
          {message.content}
          {message.commit?.updatedNote && (
            <div className={styles.commitMeta}>
              <div className={styles.commitRow}>
                {queuedInHiddenInbox ? (
                  <>
                    Queued in <strong>{message.commit.updatedNote.notebookName ?? "Notebook"}</strong>
                  </>
                ) : queuedForLater ? (
                  <>
                    Queued in <strong>{message.commit.updatedNote.notebookName ?? "Notebook"}</strong>
                    {" / "}
                    <strong>{message.commit.updatedNote.title}</strong>
                  </>
                ) : (
                  <>
                    Routed to <strong>{message.commit.updatedNote.notebookName ?? "Notebook"}</strong>
                    {" / "}
                    <strong>{message.commit.updatedNote.title}</strong>
                  </>
                )}
              </div>
              {message.commit.diff.length > 0 && (
                <div className={styles.commitDiff}>
                  {message.commit.diff.map((entry) => (
                    <div key={`${message.id}-${entry.sectionId}`} className={styles.diffItem}>
                      <span className={styles.diffLabel}>{entry.label}</span>
                      <span className={styles.diffType}>{entry.changeType}</span>
                    </div>
                  ))}
                </div>
              )}
              {message.commit.provenance && (
                <div className={styles.provenance}>
                  {message.commit.provenance.providerName} - confidence{" "}
                  {(message.commit.provenance.routeConfidence * 100).toFixed(0)}%
                </div>
              )}
              {message.undo && (
                <button
                  className={styles.undoBtn}
                  onClick={() => onUndo(message.undo!.noteId, message.undo!.operationId)}
                >
                  Undo
                </button>
              )}
            </div>
          )}
        </div>
      </div>
      <div className={styles.meta}>
        <span className={styles.time}>{formatTime(message.createdAt)}</span>
      </div>
    </div>
  );
}
