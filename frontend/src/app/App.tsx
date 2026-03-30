import { useCallback, useEffect, useState } from "react";
import { MessageSquare } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { ChatPanel } from "../components/chat/ChatPanel";
import { EditorView } from "../components/editor/EditorView";
import { Sidebar } from "../components/layout/Sidebar";
import { useChat } from "../hooks/useChat";
import { useBreakpoints } from "../hooks/useMediaQuery";
import { useNoteEditor } from "../hooks/useNoteEditor";
import { useNotebooks } from "../hooks/useNotebooks";
import { useTheme } from "../hooks/useTheme";
import styles from "./App.module.css";

export function App() {
  const { theme, toggleTheme } = useTheme();
  const { isMobile } = useBreakpoints();

  // --- State hooks ---
  const {
    notebooks,
    activeNotebook,
    activeNotebookId,
    isLoading: notebooksLoading,
    selectNotebook,
    createNotebook,
  } = useNotebooks();

  const noteEditor = useNoteEditor(activeNotebookId);

  const chat = useChat({
    activeNotebookId,
    selectedNoteId: noteEditor.draftMode ? null : noteEditor.selectedNoteId,
    selectedNoteRevisionId: noteEditor.draftMode ? null : noteEditor.selectedNote?.currentRevisionId ?? null,
    draftMode: noteEditor.draftMode,
    syncUpdatedNote: noteEditor.syncUpdatedNote,
    setActiveNotebookId: selectNotebook,
    refreshNotebook: noteEditor.refreshNotebook,
  });

  // --- UI state ---
  const [chatOpen, setChatOpen] = useState(false);
  const [mobileView, setMobileView] = useState<"notes" | "editor" | "chat">("notes");

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const mod = e.ctrlKey || e.metaKey;
      if (mod && e.key === "s") {
        e.preventDefault();
        void noteEditor.savePage();
      }
      if (mod && e.key === "n" && !e.shiftKey) {
        e.preventDefault();
        noteEditor.createDraftPage();
      }
      if (mod && (e.key === "k" || e.key === "/")) {
        e.preventDefault();
        setChatOpen((v) => !v);
      }
    };
    window.addEventListener("keydown", handler, true);
    return () => window.removeEventListener("keydown", handler, true);
  }, [noteEditor]);

  const handleSelectNotebook = useCallback((id: number) => {
    noteEditor.clearForNotebookSwitch();
    selectNotebook(id);
  }, [noteEditor, selectNotebook]);

  const handleSelectNote = useCallback((id: number) => {
    noteEditor.selectNote(id);
    if (isMobile) setMobileView("editor");
  }, [noteEditor, isMobile]);

  const handleCreateDraft = useCallback(() => {
    noteEditor.createDraftPage();
    if (isMobile) setMobileView("editor");
  }, [noteEditor, isMobile]);

  // --- Mobile: show only one view at a time ---
  if (isMobile) {
    return (
      <div className={styles.root}>
        {mobileView === "notes" && (
          <Sidebar
            notebooks={notebooks}
            activeNotebookId={activeNotebookId}
            notes={noteEditor.notes}
            selectedNoteId={noteEditor.selectedNoteId}
            draftMode={noteEditor.draftMode}
            loading={notebooksLoading}
            theme={theme}
            onToggleTheme={toggleTheme}
            onSelectNotebook={handleSelectNotebook}
            onSelectNote={handleSelectNote}
            onCreateNotebook={createNotebook}
            onCreateNote={handleCreateDraft}
          />
        )}
        {mobileView === "editor" && (
          <div className={styles.editorArea}>
            <EditorView
              editorKey={noteEditor.editorKey}
              initialContent={noteEditor.editorValue}
              selectedNote={noteEditor.selectedNote}
              draftMode={noteEditor.draftMode}
              isDirty={noteEditor.isDirty}
              busy={noteEditor.busy || chat.busy}
              theme={theme}
              onChange={noteEditor.onEditorChange}
              onSave={() => { void noteEditor.savePage(); }}
              onBack={() => setMobileView("notes")}
              isMobile
            />
          </div>
        )}
        {mobileView === "chat" && (
          <ChatPanel
            messages={chat.messages}
            busy={chat.busy}
            onSubmit={chat.submit}
            onUndo={chat.undo}
            onClose={() => setMobileView("editor")}
            isMobile
          />
        )}
        {/* Mobile bottom nav */}
        <MobileNav active={mobileView} onNavigate={setMobileView} />
      </div>
    );
  }

  // --- Desktop / Tablet ---
  return (
    <div className={styles.root} data-chat-open={chatOpen}>
      <Sidebar
        notebooks={notebooks}
        activeNotebookId={activeNotebookId}
        notes={noteEditor.notes}
        selectedNoteId={noteEditor.selectedNoteId}
        draftMode={noteEditor.draftMode}
        loading={notebooksLoading}
        theme={theme}
        onToggleTheme={toggleTheme}
        onSelectNotebook={handleSelectNotebook}
        onSelectNote={handleSelectNote}
        onCreateNotebook={createNotebook}
        onCreateNote={handleCreateDraft}
      />

      <div className={styles.editorArea}>
        <EditorView
          editorKey={noteEditor.editorKey}
          initialContent={noteEditor.editorValue}
          selectedNote={noteEditor.selectedNote}
          draftMode={noteEditor.draftMode}
          isDirty={noteEditor.isDirty}
          busy={noteEditor.busy || chat.busy}
          theme={theme}
          onChange={noteEditor.onEditorChange}
          onSave={() => { void noteEditor.savePage(); }}
        />
      </div>

      <AnimatePresence>
        {chatOpen && (
          <ChatPanel
            messages={chat.messages}
            busy={chat.busy}
            onSubmit={chat.submit}
            onUndo={chat.undo}
            onClose={() => setChatOpen(false)}
          />
        )}
      </AnimatePresence>

      {!chatOpen && (
        <button
          className={styles.chatFab}
          onClick={() => setChatOpen(true)}
          aria-label="Open chat"
          title="Chat (Ctrl+K)"
        >
          <MessageSquare size={22} />
          <span className={styles.chatFabHint}>K</span>
        </button>
      )}
    </div>
  );
}

// Simple mobile bottom nav — will be extracted to its own file in Phase F
function MobileNav({
  active,
  onNavigate,
}: {
  active: "notes" | "editor" | "chat";
  onNavigate: (view: "notes" | "editor" | "chat") => void;
}) {
  const navStyle: React.CSSProperties = {
    position: "fixed",
    bottom: 0,
    left: 0,
    right: 0,
    height: 56,
    background: "var(--surface)",
    borderTop: "1px solid var(--border)",
    display: "flex",
    alignItems: "center",
    justifyContent: "space-around",
    zIndex: 40,
  };
  const btnStyle = (isActive: boolean): React.CSSProperties => ({
    background: "none",
    border: "none",
    color: isActive ? "var(--accent)" : "var(--text-muted)",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: 2,
    fontSize: "0.6875rem",
    fontWeight: isActive ? 600 : 400,
    padding: "6px 16px",
  });

  return (
    <nav style={navStyle}>
      <button style={btnStyle(active === "notes")} onClick={() => onNavigate("notes")}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20"/></svg>
        Notes
      </button>
      <button style={btnStyle(active === "editor")} onClick={() => onNavigate("editor")}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
        Editor
      </button>
      <button style={btnStyle(active === "chat")} onClick={() => onNavigate("chat")}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/></svg>
        Chat
      </button>
    </nav>
  );
}

export default App;
