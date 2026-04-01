import { useMemo, useState } from "react";
import { BookOpen, FileText, Moon, Plus, Search, Sun } from "lucide-react";
import { motion } from "motion/react";
import { staggerChildren, staggerItem } from "../../lib/animations";
import { extractPlainText } from "../../lib/textUtils";
import type { Notebook, NoteSummary } from "../../lib/types";
import { CreateNotebookModal } from "../shared/CreateNotebookModal";
import styles from "./Sidebar.module.css";

interface SidebarProps {
  notebooks: Notebook[];
  activeNotebookId: number | null;
  notes: NoteSummary[];
  selectedNoteId: number | null;
  draftMode: boolean;
  loading: boolean;
  theme: "light" | "dark";
  onToggleTheme: () => void;
  onSelectNotebook: (id: number) => void;
  onSelectNote: (id: number) => void;
  onCreateNotebook: (name: string, description: string) => Promise<Notebook | null>;
  onCreateNote: () => void;
}

function relativeDate(iso: string): string {
  const now = Date.now();
  const then = new Date(iso).getTime();
  const diff = now - then;
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "Just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return "Yesterday";
  if (days < 7) return `${days}d ago`;
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export function Sidebar({
  notebooks,
  activeNotebookId,
  notes,
  selectedNoteId,
  draftMode,
  loading,
  theme,
  onToggleTheme,
  onSelectNotebook,
  onSelectNote,
  onCreateNotebook,
  onCreateNote,
}: SidebarProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [notebookModalOpen, setNotebookModalOpen] = useState(false);

  const filteredNotes = useMemo(() => {
    if (!searchQuery.trim()) return notes;
    const q = searchQuery.toLowerCase();
    return notes.filter(
      (n) => n.title.toLowerCase().includes(q) || n.summaryShort?.toLowerCase().includes(q)
    );
  }, [notes, searchQuery]);

  return (
    <aside className={styles.sidebar}>
      {/* Header: branding + notebook selector + search */}
      <div className={styles.header}>
        <div className={styles.brand}>
          <span className={styles.brandName}>
            Notes<span className={styles.brandAccent}>Zero</span>
          </span>
          <button
            className={styles.themeBtn}
            onClick={onToggleTheme}
            aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
          >
            {theme === "dark" ? <Sun size={16} /> : <Moon size={16} />}
          </button>
        </div>

        {notebooks.length > 0 && (
          <select
            data-testid="notebook-select"
            className={styles.notebookSelect}
            value={activeNotebookId ?? ""}
            onChange={(e) => {
              const val = Number(e.target.value);
              if (val) onSelectNotebook(val);
            }}
          >
            {notebooks.map((nb) => (
              <option key={nb.id} value={nb.id}>
                {nb.name}
              </option>
            ))}
          </select>
        )}

        <div className={styles.search}>
          <Search size={14} className={styles.searchIcon} />
          <input
            className={styles.searchInput}
            type="text"
            placeholder="Search notes..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
      </div>

      {/* Note list */}
      {loading ? (
        <div className={styles.empty}>
          <span className={styles.emptyDesc}>Loading...</span>
        </div>
      ) : notebooks.length === 0 ? (
        <div className={styles.empty}>
          <BookOpen size={40} className={styles.emptyIcon} />
          <span className={styles.emptyTitle}>No notebooks yet</span>
          <span className={styles.emptyDesc}>
            Create your first notebook to start organizing notes.
          </span>
        </div>
      ) : filteredNotes.length === 0 && !draftMode ? (
        <div className={styles.empty}>
          <FileText size={40} className={styles.emptyIcon} />
          <span className={styles.emptyTitle}>
            {searchQuery ? "No matching notes" : "No notes yet"}
          </span>
          <span className={styles.emptyDesc}>
            {searchQuery
              ? "Try a different search term."
              : "Create a new note or send a chat message."}
          </span>
        </div>
      ) : (
        <motion.div
          className={styles.noteList}
          variants={staggerChildren}
          initial="initial"
          animate="animate"
        >
          {draftMode && (
            <motion.button
              variants={staggerItem}
              className={`${styles.noteItem} ${styles.noteItemActive}`}
            >
              <span className={styles.accentBar} />
              <span className={styles.noteTitle}>Untitled draft</span>
              <span className={styles.notePreview}>New note...</span>
            </motion.button>
          )}
          {filteredNotes.map((note) => {
            const isActive = !draftMode && note.id === selectedNoteId;
            return (
              <motion.button
                key={note.id}
                data-testid={`note-tab-${note.id}`}
                variants={staggerItem}
                className={`${styles.noteItem} ${isActive ? styles.noteItemActive : ""}`}
                onClick={() => onSelectNote(note.id)}
              >
                <span className={styles.accentBar} />
                <span className={styles.noteTitle}>{note.title || "Untitled"}</span>
                {note.summaryShort && (
                  <span className={styles.notePreview}>{note.summaryShort}</span>
                )}
                <span className={styles.noteDate}>{relativeDate(note.updatedAt)}</span>
              </motion.button>
            );
          })}
        </motion.div>
      )}

      {/* Footer actions */}
      <div className={styles.footer}>
        {activeNotebookId && (
          <button className={styles.primaryBtn} onClick={onCreateNote}>
            <Plus size={14} />
            New Note
          </button>
        )}
        <button className={styles.secondaryBtn} onClick={() => setNotebookModalOpen(true)}>
          <Plus size={14} />
          New Notebook
        </button>
      </div>

      <CreateNotebookModal
        opened={notebookModalOpen}
        onClose={() => setNotebookModalOpen(false)}
        onCreate={onCreateNotebook}
      />
    </aside>
  );
}
