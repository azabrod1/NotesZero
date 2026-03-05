import { useEffect, useMemo, useState } from "react";
import { ChatDock } from "../components/ChatDock";
import { ClarificationPanel } from "../components/ClarificationPanel";
import { NotebookSidebar } from "../components/NotebookSidebar";
import { PageTabs } from "../components/PageTabs";
import { RichTextEditor } from "../components/RichTextEditor";
import { useTheme } from "../hooks/useTheme";
import { apiClient } from "../lib/apiClient";
import { ensureHtml, escapeHtml, stripHtml } from "../lib/textUtils";
import { ChatMessage, ChatMode, ClarificationTask, Note, Notebook } from "../lib/types";
import "./app.css";

function messageId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function App() {
  const { theme, toggleTheme } = useTheme();

  const [notebooks, setNotebooks] = useState<Notebook[]>([]);
  const [activeNotebookId, setActiveNotebookId] = useState<number | null>(null);
  const [notes, setNotes] = useState<Note[]>([]);
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null);
  const [draftMode, setDraftMode] = useState<boolean>(false);
  const [editorValue, setEditorValue] = useState<string>("<p></p>");
  const [isDirty, setIsDirty] = useState<boolean>(false);
  const [clarifications, setClarifications] = useState<ClarificationTask[]>([]);

  const [busy, setBusy] = useState<boolean>(false);
  const [statusLine, setStatusLine] = useState<string>("Ready.");

  const [chatMode, setChatMode] = useState<ChatMode>("capture");
  const [chatMinimized, setChatMinimized] = useState<boolean>(false);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([
    {
      id: messageId(),
      role: "assistant",
      mode: "capture",
      content: "Capture notes, ask your notebook, or edit the current page from this dock.",
      createdAt: new Date().toISOString()
    }
  ]);

  const activeNotebook = useMemo(
    () => notebooks.find((notebook) => notebook.id === activeNotebookId) ?? null,
    [activeNotebookId, notebooks]
  );

  const selectedNote = useMemo(
    () => notes.find((note) => note.id === selectedNoteId) ?? null,
    [notes, selectedNoteId]
  );

  useEffect(() => {
    void bootstrap();
  }, []);

  useEffect(() => {
    if (activeNotebookId !== null) {
      void refreshNotebook(activeNotebookId);
    }
  }, [activeNotebookId]);

  useEffect(() => {
    if (draftMode) {
      return;
    }
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

  const bootstrap = async () => {
    setBusy(true);
    try {
      const [notebookItems, clarificationItems] = await Promise.all([
        apiClient.listNotebooks(),
        apiClient.listClarifications()
      ]);
      setNotebooks(notebookItems);
      setClarifications(clarificationItems);
      if (notebookItems.length > 0) {
        setActiveNotebookId(notebookItems[0].id);
      }
      setStatusLine("Workspace loaded.");
    } catch (error) {
      setStatusLine(`Failed to load workspace: ${(error as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const refreshNotebook = async (notebookId: number) => {
    setBusy(true);
    try {
      const [noteItems, clarificationItems] = await Promise.all([
        apiClient.listNotes(notebookId),
        apiClient.listClarifications()
      ]);
      setNotes(noteItems);
      setClarifications(clarificationItems);
      if (!draftMode && noteItems.length > 0) {
        const stillExists = selectedNoteId !== null && noteItems.some((note) => note.id === selectedNoteId);
        if (!stillExists) {
          setSelectedNoteId(noteItems[0].id);
        }
      }
    } catch (error) {
      setStatusLine(`Failed to load notebook: ${(error as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const createDraftPage = () => {
    setDraftMode(true);
    setSelectedNoteId(null);
    setEditorValue("<h1>Untitled page</h1><p></p>");
    setIsDirty(true);
  };

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
    } catch (error) {
      setStatusLine(`Failed to save page: ${(error as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const resolveClarification = async (taskId: number, selectedOption: string) => {
    setBusy(true);
    try {
      await apiClient.resolveClarification(taskId, { selectedOption });
      if (activeNotebookId !== null) {
        await refreshNotebook(activeNotebookId);
      }
      setStatusLine("Clarification resolved.");
    } catch (error) {
      setStatusLine(`Failed to resolve clarification: ${(error as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const appendChatMessage = (message: ChatMessage) => {
    setChatMessages((current) => [...current, message]);
  };

  const submitChat = async (text: string) => {
    appendChatMessage({
      id: messageId(),
      role: "user",
      mode: chatMode,
      content: text,
      createdAt: new Date().toISOString()
    });

    if (activeNotebookId === null) {
      appendChatMessage({
        id: messageId(),
        role: "assistant",
        mode: chatMode,
        content: "Select a notebook first.",
        createdAt: new Date().toISOString()
      });
      return;
    }

    setBusy(true);
    try {
      if (chatMode === "capture") {
        const created = await apiClient.createNote({
          rawText: text,
          sourceType: "TEXT",
          notebookId: activeNotebookId
        });
        await refreshNotebook(activeNotebookId);
        setStatusLine("Captured as a new page.");
        appendChatMessage({
          id: messageId(),
          role: "assistant",
          mode: chatMode,
          content: `Saved to ${created.notebookName ?? activeNotebook?.name ?? "notebook"}.`,
          createdAt: new Date().toISOString()
        });
        return;
      }

      if (chatMode === "query") {
        const answer = await apiClient.askNotebook({ notebookId: activeNotebookId, question: text });
        appendChatMessage({
          id: messageId(),
          role: "assistant",
          mode: chatMode,
          content: `${answer.answer}\nSources: ${answer.citedNoteIds.join(", ") || "none"}`,
          createdAt: new Date().toISOString()
        });
        setStatusLine("Notebook query answered.");
        return;
      }

      if (!selectedNoteId) {
        appendChatMessage({
          id: messageId(),
          role: "assistant",
          mode: chatMode,
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
      appendChatMessage({
        id: messageId(),
        role: "assistant",
        mode: chatMode,
        content: "Added that to the current page.",
        createdAt: new Date().toISOString()
      });
      setStatusLine("Page updated from chat.");
    } catch (error) {
      appendChatMessage({
        id: messageId(),
        role: "assistant",
        mode: chatMode,
        content: `Request failed: ${(error as Error).message}`,
        createdAt: new Date().toISOString()
      });
      setStatusLine(`Chat request failed: ${(error as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={chatMinimized ? "app-root chat-minimized" : "app-root"}>
      <header className="topbar">
        <div className="brand">
          <h1>Notebook Workspace</h1>
          <span>{activeNotebook ? activeNotebook.name : "No notebook selected"}</span>
        </div>

        <div className="topbar-actions">
          <button className="ghost-button" type="button" onClick={createDraftPage}>
            New Page
          </button>
          <button className="primary-button" type="button" onClick={savePage} disabled={!isDirty || busy}>
            {busy ? "Saving..." : "Save"}
          </button>
          <button className="ghost-button" type="button" onClick={toggleTheme}>
            {theme === "light" ? "Dark mode" : "Light mode"}
          </button>
        </div>
      </header>

      <main className="workspace">
        <NotebookSidebar
          notebooks={notebooks}
          activeNotebookId={activeNotebookId}
          onSelectNotebook={(id) => {
            setDraftMode(false);
            setActiveNotebookId(id);
            setSelectedNoteId(null);
          }}
        />

        <section className="note-workbench">
          <PageTabs
            notes={notes}
            selectedNoteId={draftMode ? null : selectedNoteId}
            onSelectNote={(noteId) => {
              setDraftMode(false);
              setSelectedNoteId(noteId);
            }}
            onCreatePage={createDraftPage}
          />

          <ClarificationPanel tasks={clarifications} onResolve={resolveClarification} />

          <section className="editor-pane">
            <RichTextEditor
              value={editorValue}
              onChange={(value) => {
                setEditorValue(value);
                setIsDirty(true);
              }}
            />
          </section>

          <footer className="note-status">
            <span>{statusLine}</span>
            {selectedNote?.createdAt && <small>Last saved: {new Date(selectedNote.createdAt).toLocaleString()}</small>}
          </footer>
        </section>
      </main>

      <ChatDock
        messages={chatMessages}
        mode={chatMode}
        minimized={chatMinimized}
        busy={busy}
        onModeChange={setChatMode}
        onSubmit={submitChat}
        onToggleMinimized={() => setChatMinimized((current) => !current)}
      />
    </div>
  );
}

export default App;
