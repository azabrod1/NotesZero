import { useCallback, useEffect, useMemo, useState } from "react";
import { ChatDock } from "../components/chat/ChatDock";
import { ClarificationBanner } from "../components/ClarificationBanner";
import { NoteEditor } from "../components/editor/NoteEditor";
import { PageTabBar } from "../components/layout/PageTabBar";
import { Sidebar } from "../components/layout/Sidebar";
import { TopBar } from "../components/layout/TopBar";
import { useTheme } from "../hooks/useTheme";
import { apiClient } from "../lib/apiClient";
import { ensureHtml, escapeHtml, stripHtml } from "../lib/textUtils";
import type { ChatMessage, ChatMode, ClarificationTask, Note, Notebook } from "../lib/types";
import styles from "./App.module.css";

function msgId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function App() {
  const { theme, toggleTheme } = useTheme();

  /* ── data state ── */
  const [notebooks, setNotebooks] = useState<Notebook[]>([]);
  const [activeNotebookId, setActiveNotebookId] = useState<number | null>(null);
  const [notes, setNotes] = useState<Note[]>([]);
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null);
  const [draftMode, setDraftMode] = useState(false);
  const [editorValue, setEditorValue] = useState("<p></p>");
  const [isDirty, setIsDirty] = useState(false);
  const [clarifications, setClarifications] = useState<ClarificationTask[]>([]);

  /* ── ui state ── */
  const [busy, setBusy] = useState(false);
  const [statusLine, setStatusLine] = useState("Ready.");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  /* ── chat state ── */
  const [chatMode, setChatMode] = useState<ChatMode>("capture");
  const [chatMinimized, setChatMinimized] = useState(false);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([
    {
      id: msgId(),
      role: "assistant",
      mode: "capture",
      content: "Capture notes, ask your notebook, or edit the current page.",
      createdAt: new Date().toISOString()
    }
  ]);

  /* ── derived ── */
  const activeNotebook = useMemo(
    () => notebooks.find((nb) => nb.id === activeNotebookId) ?? null,
    [activeNotebookId, notebooks]
  );

  const selectedNote = useMemo(
    () => notes.find((n) => n.id === selectedNoteId) ?? null,
    [notes, selectedNoteId]
  );

  /* ── bootstrap ── */
  useEffect(() => {
    void bootstrap();
  }, []);

  useEffect(() => {
    if (activeNotebookId !== null) void refreshNotebook(activeNotebookId);
  }, [activeNotebookId]);

  useEffect(() => {
    if (draftMode) return;
    if (selectedNote) {
      setEditorValue(ensureHtml(selectedNote.rawText ?? ""));
      setIsDirty(false);
      return;
    }
    if (notes.length > 0) {
      setSelectedNoteId(notes[0].id);
      return;
    }
    setEditorValue("<p></p>");
    setSelectedNoteId(null);
  }, [selectedNote, notes, draftMode]);

  /* ── keyboard shortcuts ── */
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === "s") {
        e.preventDefault();
        void savePage();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  });

  /* ── api helpers ── */
  const bootstrap = async () => {
    setBusy(true);
    try {
      const [nbs, cls] = await Promise.all([
        apiClient.listNotebooks(),
        apiClient.listClarifications()
      ]);
      setNotebooks(nbs);
      setClarifications(cls);
      if (nbs.length > 0) setActiveNotebookId(nbs[0].id);
      setStatusLine("Workspace loaded.");
    } catch (err) {
      setStatusLine(`Failed to load: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const refreshNotebook = async (nbId: number) => {
    setBusy(true);
    try {
      const [noteItems, cls] = await Promise.all([
        apiClient.listNotes(nbId),
        apiClient.listClarifications()
      ]);
      setNotes(noteItems);
      setClarifications(cls);
      if (!draftMode && noteItems.length > 0) {
        const exists = selectedNoteId !== null && noteItems.some((n) => n.id === selectedNoteId);
        if (!exists) setSelectedNoteId(noteItems[0].id);
      }
    } catch (err) {
      setStatusLine(`Failed to load notebook: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const createDraftPage = useCallback(() => {
    setDraftMode(true);
    setSelectedNoteId(null);
    setEditorValue("<h1>Untitled page</h1><p></p>");
    setIsDirty(true);
  }, []);

  const savePage = async () => {
    if (!activeNotebookId) {
      setStatusLine("Select a notebook first.");
      return;
    }
    if (!stripHtml(editorValue).trim()) {
      setStatusLine("Cannot save an empty page.");
      return;
    }
    setBusy(true);
    try {
      if (draftMode || selectedNoteId === null) {
        const created = await apiClient.createNote({
          rawText: editorValue,
          sourceType: "TEXT",
          notebookId: activeNotebookId
        });
        setDraftMode(false);
        setSelectedNoteId(created.id);
        setStatusLine(`Page created in ${activeNotebook?.name ?? "notebook"}.`);
      } else {
        await apiClient.updateNote(selectedNoteId, {
          rawText: editorValue,
          notebookId: activeNotebookId,
          occurredAt: selectedNote?.occurredAt ?? null
        });
        setStatusLine("Page saved.");
      }
      setIsDirty(false);
      await refreshNotebook(activeNotebookId);
    } catch (err) {
      setStatusLine(`Save failed: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const resolveClarification = async (taskId: number, selectedOption: string) => {
    setBusy(true);
    try {
      await apiClient.resolveClarification(taskId, { selectedOption });
      if (activeNotebookId !== null) await refreshNotebook(activeNotebookId);
      setStatusLine("Clarification resolved.");
    } catch (err) {
      setStatusLine(`Clarification failed: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  /* ── chat ── */
  const pushMsg = (msg: ChatMessage) => setChatMessages((prev) => [...prev, msg]);

  const submitChat = async (text: string) => {
    pushMsg({
      id: msgId(), role: "user", mode: chatMode,
      content: text, createdAt: new Date().toISOString()
    });

    if (activeNotebookId === null) {
      pushMsg({
        id: msgId(), role: "assistant", mode: chatMode,
        content: "Select a notebook first.", createdAt: new Date().toISOString()
      });
      return;
    }

    setBusy(true);
    try {
      if (chatMode === "capture") {
        const created = await apiClient.createNote({
          rawText: text, sourceType: "TEXT", notebookId: activeNotebookId
        });
        await refreshNotebook(activeNotebookId);
        setStatusLine("Captured as a new page.");
        pushMsg({
          id: msgId(), role: "assistant", mode: chatMode,
          content: `Saved to ${created.notebookName ?? activeNotebook?.name ?? "notebook"}.`,
          createdAt: new Date().toISOString()
        });
      } else if (chatMode === "query") {
        const answer = await apiClient.askNotebook({ notebookId: activeNotebookId, question: text });
        pushMsg({
          id: msgId(), role: "assistant", mode: chatMode,
          content: `${answer.answer}\nSources: ${answer.citedNoteIds.join(", ") || "none"}`,
          createdAt: new Date().toISOString()
        });
        setStatusLine("Query answered.");
      } else {
        if (!selectedNoteId) {
          pushMsg({
            id: msgId(), role: "assistant", mode: chatMode,
            content: "Open or create a page before using edit mode.",
            createdAt: new Date().toISOString()
          });
          return;
        }
        const updatedHtml = `${editorValue}<p>${escapeHtml(text)}</p>`;
        await apiClient.updateNote(selectedNoteId, {
          rawText: updatedHtml,
          notebookId: activeNotebookId,
          occurredAt: selectedNote?.occurredAt ?? null
        });
        setEditorValue(updatedHtml);
        setIsDirty(false);
        await refreshNotebook(activeNotebookId);
        pushMsg({
          id: msgId(), role: "assistant", mode: chatMode,
          content: "Added that to the current page.",
          createdAt: new Date().toISOString()
        });
        setStatusLine("Page updated from chat.");
      }
    } catch (err) {
      pushMsg({
        id: msgId(), role: "assistant", mode: chatMode,
        content: `Request failed: ${(err as Error).message}`,
        createdAt: new Date().toISOString()
      });
      setStatusLine(`Chat failed: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  /* ── render ── */
  return (
    <div className={styles.root}>
      <TopBar
        notebookName={activeNotebook?.name ?? null}
        theme={theme}
        onToggleTheme={toggleTheme}
        onToggleSidebar={() => setSidebarCollapsed((c) => !c)}
        statusText={isDirty ? "Unsaved changes" : statusLine}
      />

      <div className={styles.body}>
        <Sidebar
          notebooks={notebooks}
          activeNotebookId={activeNotebookId}
          collapsed={sidebarCollapsed}
          onSelectNotebook={(id) => {
            setDraftMode(false);
            setActiveNotebookId(id);
            setSelectedNoteId(null);
          }}
        />

        <main className={styles.main}>
          <PageTabBar
            notes={notes}
            selectedNoteId={selectedNoteId}
            draftMode={draftMode}
            onSelectNote={(id) => {
              setDraftMode(false);
              setSelectedNoteId(id);
            }}
            onCreatePage={createDraftPage}
          />

          <ClarificationBanner
            tasks={clarifications}
            onResolve={resolveClarification}
          />

          <div className={styles.editorArea}>
            <NoteEditor
              value={editorValue}
              onChange={(html) => {
                setEditorValue(html);
                setIsDirty(true);
              }}
              placeholder="Start writing your note..."
            />
          </div>

          <ChatDock
            messages={chatMessages}
            mode={chatMode}
            minimized={chatMinimized}
            busy={busy}
            onModeChange={setChatMode}
            onSubmit={submitChat}
            onToggleMinimized={() => setChatMinimized((c) => !c)}
          />
        </main>
      </div>
    </div>
  );
}

export default App;
