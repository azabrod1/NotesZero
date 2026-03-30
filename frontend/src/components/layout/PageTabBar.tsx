import { FilePlus, FileText } from "lucide-react";
import type { NoteSummary } from "../../lib/types";
import styles from "./PageTabBar.module.css";

interface PageTabBarProps {
  notes: NoteSummary[];
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
            data-testid={`note-tab-${note.id}`}
          >
            <FileText size={14} />
            <span className={styles.tabLabel}>{note.title}</span>
          </button>
        );
      })}

      {draftMode && (
        <button className={`${styles.tab} ${styles.tabActive}`} data-testid="note-tab-draft">
          <FileText size={14} />
          <span className={styles.tabLabel}>Untitled page</span>
        </button>
      )}

      <button className={styles.newTab} onClick={onCreatePage} data-testid="note-tab-new">
        <FilePlus size={14} />
        New
      </button>
    </div>
  );
}
