import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChatDock } from "../components/chat/ChatDock";
import { NoteEditor } from "../components/editor/NoteEditor";
import { PageTabBar } from "../components/layout/PageTabBar";
import { Sidebar } from "../components/layout/Sidebar";
import { TopBar } from "../components/layout/TopBar";
import { useTheme } from "../hooks/useTheme";
import { apiClient } from "../lib/apiClient";
import { isBlockNoteEmpty } from "../lib/textUtils";
import type { ChatMessage, CommitChatResponse, Note, NoteSummary, Notebook } from "../lib/types";
import styles from "./App.module.css";

function msgId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function createGenericDraftJson(): string {
  return "[]";
}

function isSystemInboxTitle(title?: string | null): boolean {
  return (title ?? "").trim().toLowerCase() === "inbox";
}

function isHiddenInboxCommit(result: CommitChatResponse): boolean {
  return Boolean(result.patchPlan.fallbackToInbox && isSystemInboxTitle(result.updatedNote?.title));
}

function assistantSummary(result: CommitChatResponse): string {
  if (result.answer) {
    return result.answer;
  }
  if (!result.updatedNote || !result.applyResult) {
    return "No note changes were applied.";
  }
  if (isHiddenInboxCommit(result)) {
    return `Queued this in ${result.updatedNote.notebookName ?? "the selected notebook"} for later organization.`;
  }
  if (result.patchPlan.fallbackToInbox) {
    return `Queued this in "${result.updatedNote.title}" for later organization.`;
  }
  const sectionCount = result.applyResult.changedSectionIds.length;
  const sectionText = sectionCount === 1 ? "1 section" : `${sectionCount} sections`;
  return `Updated "${result.updatedNote.title}" in ${result.updatedNote.notebookName ?? "the selected notebook"} and changed ${sectionText}.`;
}

export function App() {
  const { theme, toggleTheme } = useTheme();

  const [notebooks, setNotebooks] = useState<Notebook[]>([]);
  const [activeNotebookId, setActiveNotebookId] = useState<number | null>(null);
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null);
  const [selectedNote, setSelectedNote] = useState<Note | null>(null);
  const [draftMode, setDraftMode] = useState(false);
  const [editorValue, setEditorValue] = useState("[]");
  const [isDirty, setIsDirty] = useState(false);

  const [busy, setBusy] = useState(false);
  const [statusLine, setStatusLine] = useState("Ready.");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [chatMinimized, setChatMinimized] = useState(false);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([
    {
      id: msgId(),
      role: "assistant",
      content: "Message a thought, edit request, or question. NotesZero will route it, patch the note, and keep an undo trail.",
      createdAt: new Date().toISOString()
    }
  ]);

  const activeNotebook = useMemo(
    () => notebooks.find((nb) => nb.id === activeNotebookId) ?? null,
    [activeNotebookId, notebooks]
  );
  const selectedNoteIdRef = useRef<number | null>(null);
  const draftModeRef = useRef(false);
  const loadSequenceRef = useRef(0);
  const loadedEditorValueRef = useRef("[]");

  const editorKey = useMemo(() => {
    if (draftMode) return "draft";
    if (selectedNoteId !== null) return `note-${selectedNoteId}-${selectedNote?.currentRevisionId ?? 0}`;
    return "empty";
  }, [draftMode, selectedNote?.currentRevisionId, selectedNoteId]);

  useEffect(() => {
    void bootstrap();
  }, []);

  useEffect(() => {
    if (activeNotebookId !== null) {
      void refreshNotebook(activeNotebookId);
    }
  }, [activeNotebookId]);

  useEffect(() => {
    if (draftMode || selectedNoteId === null) {
      return;
    }
    void loadNote(selectedNoteId);
  }, [selectedNoteId, draftMode]);

  useEffect(() => {
    selectedNoteIdRef.current = selectedNoteId;
  }, [selectedNoteId]);

  useEffect(() => {
    draftModeRef.current = draftMode;
  }, [draftMode]);

  useEffect(() => {
    if (draftMode) {
      return;
    }
    if (selectedNote) {
      loadedEditorValueRef.current = selectedNote.editorContent ?? "[]";
      setEditorValue(loadedEditorValueRef.current);
      setIsDirty(false);
      return;
    }
    if (selectedNoteId === null) {
      loadedEditorValueRef.current = "[]";
      setEditorValue("[]");
      setIsDirty(false);
    }
  }, [selectedNote, selectedNoteId, draftMode]);

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

  const bootstrap = async () => {
    setBusy(true);
    try {
      const nbs = await apiClient.listNotebooks();
      setNotebooks(nbs);
      if (nbs.length > 0) {
        setActiveNotebookId(nbs[0].id);
      }
      setStatusLine("Workspace loaded.");
    } catch (err) {
      setStatusLine(`Failed to load workspace: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const refreshNotebook = async (notebookId: number, preferredNoteId?: number | null) => {
    const noteItems = await apiClient.listNotes(notebookId);
    setNotes(noteItems);
    if (!draftModeRef.current) {
      const resolvedPreferredNoteId = preferredNoteId ?? selectedNoteIdRef.current;
      if (noteItems.length === 0) {
        setSelectedNoteId(null);
        setSelectedNote(null);
      } else if (
        resolvedPreferredNoteId === null ||
        !noteItems.some((note) => note.id === resolvedPreferredNoteId)
      ) {
        setSelectedNote(null);
        setSelectedNoteId(noteItems[0].id);
      }
    }
  };

  const loadNote = async (noteId: number) => {
    const requestId = ++loadSequenceRef.current;
    try {
      const note = await apiClient.getNote(noteId);
      if (
        requestId !== loadSequenceRef.current ||
        draftModeRef.current ||
        selectedNoteIdRef.current !== noteId
      ) {
        return;
      }
      setSelectedNote(note);
    } catch (err) {
      setStatusLine(`Failed to load note: ${(err as Error).message}`);
    }
  };

  const createNotebook = useCallback(async () => {
    const name = window.prompt("Notebook name:");
    if (!name?.trim()) return;
    const description = window.prompt("Short routing description:") ?? "";
    setBusy(true);
    try {
      const notebook = await apiClient.createNotebook({
        name: name.trim(),
        description: description.trim() || name.trim()
      });
      setNotebooks((prev) => [...prev, notebook]);
      setActiveNotebookId(notebook.id);
      setSelectedNoteId(null);
      setSelectedNote(null);
      setDraftMode(false);
      setStatusLine(`Created notebook "${notebook.name}".`);
    } catch (err) {
      setStatusLine(`Failed to create notebook: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  }, []);

  const createDraftPage = useCallback(() => {
    setDraftMode(true);
    setSelectedNoteId(null);
    setSelectedNote(null);
    setEditorValue(createGenericDraftJson());
    loadedEditorValueRef.current = createGenericDraftJson();
    setIsDirty(false);
  }, []);

  const savePage = async () => {
    if (!activeNotebookId) {
      setStatusLine("Select a notebook first.");
      return;
    }
    if (isBlockNoteEmpty(editorValue)) {
      setStatusLine("Cannot save an empty note.");
      return;
    }

    setBusy(true);
    try {
      if (draftMode || selectedNoteId === null) {
        const created = await apiClient.createNote({
          notebookId: activeNotebookId,
          noteType: "generic_note/v1",
          editorContent: editorValue
        });
        setDraftMode(false);
        setSelectedNoteId(created.id);
        setSelectedNote(created);
        loadedEditorValueRef.current = created.editorContent;
        setStatusLine(`Created "${created.title}".`);
        await refreshNotebook(activeNotebookId, created.id);
      } else {
        const updated = await apiClient.updateNote(selectedNoteId, {
          notebookId: activeNotebookId,
          editorContent: editorValue,
          currentRevisionId: selectedNote?.currentRevisionId ?? null
        });
        setSelectedNote(updated);
        loadedEditorValueRef.current = updated.editorContent;
        setStatusLine("Note saved.");
        await refreshNotebook(activeNotebookId, updated.id);
      }
      setIsDirty(false);
    } catch (err) {
      setStatusLine(`Save failed: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const pushMessage = (message: ChatMessage) => {
    setChatMessages((prev) => [...prev, message]);
  };

  const recentChatEventIds = (): number[] =>
    chatMessages
      .map((message) => message.commit?.chatEventId ?? null)
      .filter((value): value is number => value !== null)
      .slice(-3);

  const syncUpdatedNote = async (note: Note) => {
    if (isSystemInboxTitle(note.title)) {
      if (note.notebookId != null && note.notebookId === activeNotebookId) {
        await refreshNotebook(note.notebookId, selectedNoteIdRef.current);
      }
      return;
    }
    setDraftMode(false);
    setSelectedNoteId(note.id);
    setSelectedNote(note);
    setEditorValue(note.editorContent);
    loadedEditorValueRef.current = note.editorContent;
    setIsDirty(false);

    if (note.notebookId) {
      setActiveNotebookId(note.notebookId);
      await refreshNotebook(note.notebookId, note.id);
    }
  };

  const submitChat = async (text: string) => {
    pushMessage({
      id: msgId(),
      role: "user",
      content: text,
      createdAt: new Date().toISOString()
    });

    if (activeNotebookId === null) {
      pushMessage({
        id: msgId(),
        role: "assistant",
        content: "Select a notebook first.",
        createdAt: new Date().toISOString()
      });
      return;
    }

    setBusy(true);
    try {
      const result = await apiClient.commitChat({
        message: text,
        selectedNotebookId: activeNotebookId,
        selectedNoteId: draftMode ? null : selectedNoteId,
        recentChatEventIds: recentChatEventIds(),
        currentRevisionId: draftMode ? null : selectedNote?.currentRevisionId ?? null
      });

      if (result.updatedNote) {
        await syncUpdatedNote(result.updatedNote);
      }

      pushMessage({
        id: msgId(),
        role: "assistant",
        content: assistantSummary(result),
        createdAt: new Date().toISOString(),
        commit: result,
        undo: result.updatedNote && result.undoToken
          ? {
              noteId: result.updatedNote.id,
              operationId: Number(result.undoToken)
            }
          : null
      });
      setStatusLine(result.answer ? "Answer ready." : result.applyResult?.outcome ?? "Chat handled.");
    } catch (err) {
      pushMessage({
        id: msgId(),
        role: "assistant",
        content: `Request failed: ${(err as Error).message}`,
        createdAt: new Date().toISOString()
      });
      setStatusLine(`Chat failed: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const undoOperation = async (noteId: number, operationId: number) => {
    setBusy(true);
    try {
      const result = await apiClient.undo(noteId, operationId);
      await syncUpdatedNote(result.updatedNote);
      pushMessage({
        id: msgId(),
        role: "assistant",
        content: `Undo restored the previous revision of "${result.updatedNote.title}".`,
        createdAt: new Date().toISOString()
      });
      setStatusLine("Undo applied.");
    } catch (err) {
      pushMessage({
        id: msgId(),
        role: "assistant",
        content: `Undo failed: ${(err as Error).message}`,
        createdAt: new Date().toISOString()
      });
      setStatusLine(`Undo failed: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={styles.root}>
      <TopBar
        notebookName={activeNotebook?.name ?? null}
        theme={theme}
        onToggleTheme={toggleTheme}
        onToggleSidebar={() => setSidebarCollapsed((collapsed) => !collapsed)}
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
            setSelectedNote(null);
            loadedEditorValueRef.current = "[]";
            setEditorValue("[]");
            setIsDirty(false);
          }}
          onCreateNotebook={createNotebook}
        />

        <main className={styles.main}>
          <PageTabBar
            notes={notes}
            selectedNoteId={selectedNoteId}
            draftMode={draftMode}
            onSelectNote={(id) => {
              if (!draftMode && selectedNoteId === id) {
                if (selectedNote === null) {
                  void loadNote(id);
                }
                return;
              }
              setDraftMode(false);
              setSelectedNote(null);
              setSelectedNoteId(id);
              loadedEditorValueRef.current = "[]";
              setEditorValue("[]");
              setIsDirty(false);
            }}
            onCreatePage={createDraftPage}
          />

          <div className={styles.editorArea}>
            <NoteEditor
              key={editorKey}
              initialContent={editorValue}
              onChange={(json) => {
                setEditorValue(json);
                setIsDirty(json !== loadedEditorValueRef.current);
              }}
              placeholder="Start writing your note..."
              theme={theme}
            />
          </div>

          <ChatDock
            messages={chatMessages}
            minimized={chatMinimized}
            busy={busy}
            onSubmit={submitChat}
            onUndo={undoOperation}
            onToggleMinimized={() => setChatMinimized((collapsed) => !collapsed)}
          />
        </main>
      </div>
    </div>
  );
}

export default App;
