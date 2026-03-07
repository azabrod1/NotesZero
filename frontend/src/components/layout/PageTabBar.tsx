import { FilePlus, FileText } from "lucide-react";
import { noteTitle } from "../../lib/textUtils";
import type { Note } from "../../lib/types";
import styles from "./PageTabBar.module.css";

interface PageTabBarProps {
  notes: Note[];
  selectedNoteId: number | null;
  draftMode: boolean;
  onSelectNote: (id: number) => void;
  onCreatePage: () => void;
}

export function PageTabBar({
  notes,
  selectedNoteId,
  draftMode,
  onSelectNote,
  onCreatePage
}: PageTabBarProps) {
  return (
    <div className={styles.tabBar}>
      {notes.map((note) => {
        const isActive = !draftMode && note.id === selectedNoteId;
        return (
          <button
            key={note.id}
            className={`${styles.tab} ${isActive ? styles.tabActive : ""}`}
            onClick={() => onSelectNote(note.id)}
          >
            <FileText size={14} />
            <span className={styles.tabLabel}>{noteTitle(note.rawText)}</span>
          </button>
        );
      })}

      {draftMode && (
        <button className={`${styles.tab} ${styles.tabActive}`}>
          <FileText size={14} />
          <span className={styles.tabLabel}>Untitled page</span>
        </button>
      )}

      <button className={styles.newTab} onClick={onCreatePage}>
        <FilePlus size={14} />
        New
      </button>
    </div>
  );
}
