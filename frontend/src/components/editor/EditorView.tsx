import { ArrowLeft, Save } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { fadeIn } from "../../lib/animations";
import type { Note } from "../../lib/types";
import { NoteEditor } from "./NoteEditor";
import styles from "./EditorView.module.css";

interface EditorViewProps {
  editorKey: string;
  initialContent: string;
  selectedNote: Note | null;
  draftMode: boolean;
  isDirty: boolean;
  busy: boolean;
  theme: "light" | "dark";
  onChange: (json: string) => void;
  onSave: () => void;
  onBack?: () => void;
  isMobile?: boolean;
}

export function EditorView({
  editorKey,
  initialContent,
  selectedNote,
  draftMode,
  isDirty,
  busy,
  theme,
  onChange,
  onSave,
  onBack,
  isMobile,
}: EditorViewProps) {
  const showEditor = draftMode || selectedNote !== null;
  const noteTitle = selectedNote?.title || (draftMode ? "New note" : null);

  return (
    <div className={styles.root}>
      {/* Top bar with back button (mobile) + save */}
      <div className={styles.toolbar}>
        <div className={styles.toolbarLeft}>
          {onBack && (
            <button className={styles.backBtn} onClick={onBack} aria-label="Back to notes">
              <ArrowLeft size={18} />
            </button>
          )}
          {noteTitle && <span className={styles.noteTitle}>{noteTitle}</span>}
        </div>
        <div className={styles.toolbarRight}>
          {isDirty && <span className={styles.dirtyDot} title="Unsaved changes" />}
          <button
            className={styles.saveBtn}
            onClick={onSave}
            disabled={!isDirty || busy}
            title="Save (Ctrl+S)"
          >
            <Save size={14} />
            <span>{busy ? "Saving..." : "Save"}</span>
            <kbd className={styles.kbd}>S</kbd>
          </button>
        </div>
      </div>

      {/* Editor content */}
      <div className={styles.content}>
        <div className={styles.writing}>
          {showEditor ? (
            <AnimatePresence mode="wait">
              <motion.div
                key={editorKey}
                className={styles.editorMotion}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
              >
                <NoteEditor
                  key={editorKey}
                  initialContent={initialContent}
                  onChange={onChange}
                  placeholder="Start writing..."
                  theme={theme}
                />
              </motion.div>
            </AnimatePresence>
          ) : (
            <motion.div className={styles.empty} {...fadeIn}>
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" opacity="0.2">
                <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                <path d="m15 5 4 4" />
              </svg>
              <p className={styles.emptyTitle}>Select a note to start editing</p>
              <p className={styles.emptyDesc}>
                Pick a note from the sidebar, or create a new one.
              </p>
            </motion.div>
          )}
        </div>
      </div>
    </div>
  );
}
