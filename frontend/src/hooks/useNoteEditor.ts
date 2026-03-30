import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiClient } from "../lib/apiClient";
import { isBlockNoteEmpty } from "../lib/textUtils";
import type { Note, NoteSummary } from "../lib/types";
import { useAutoSave } from "./useAutoSave";

export function useNoteEditor(activeNotebookId: number | null) {
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null);
  const [selectedNote, setSelectedNote] = useState<Note | null>(null);
  const [draftMode, setDraftMode] = useState(false);
  const [editorValue, setEditorValue] = useState("[]");
  const [isDirty, setIsDirty] = useState(false);
  const [busy, setBusy] = useState(false);

  const selectedNoteIdRef = useRef<number | null>(null);
  const draftModeRef = useRef(false);
  const loadSequenceRef = useRef(0);
  const loadedEditorValueRef = useRef("[]");

  const editorKey = useMemo(() => {
    if (draftMode) return "draft";
    if (selectedNoteId !== null) return `note-${selectedNoteId}-${selectedNote?.currentRevisionId ?? 0}`;
    return "empty";
  }, [draftMode, selectedNote?.currentRevisionId, selectedNoteId]);

  // Keep refs in sync
  useEffect(() => { selectedNoteIdRef.current = selectedNoteId; }, [selectedNoteId]);
  useEffect(() => { draftModeRef.current = draftMode; }, [draftMode]);

  // Load notes list when notebook changes
  const refreshNotebook = useCallback(async (notebookId: number, preferredNoteId?: number | null) => {
    const noteItems = await apiClient.listNotes(notebookId);
    setNotes(noteItems);
    if (!draftModeRef.current) {
      const resolved = preferredNoteId ?? selectedNoteIdRef.current;
      if (noteItems.length === 0) {
        setSelectedNoteId(null);
        setSelectedNote(null);
      } else if (resolved === null || !noteItems.some((n) => n.id === resolved)) {
        setSelectedNote(null);
        setSelectedNoteId(noteItems[0].id);
      }
    }
  }, []);

  useEffect(() => {
    if (activeNotebookId !== null) {
      void refreshNotebook(activeNotebookId);
    } else {
      setNotes([]);
      setSelectedNoteId(null);
      setSelectedNote(null);
    }
  }, [activeNotebookId, refreshNotebook]);

  // Load individual note
  const loadNote = useCallback(async (noteId: number) => {
    const requestId = ++loadSequenceRef.current;
    try {
      const note = await apiClient.getNote(noteId);
      if (requestId !== loadSequenceRef.current || draftModeRef.current || selectedNoteIdRef.current !== noteId) return;
      setSelectedNote(note);
    } catch {
      // handled by caller
    }
  }, []);

  useEffect(() => {
    if (!draftMode && selectedNoteId !== null) {
      void loadNote(selectedNoteId);
    }
  }, [selectedNoteId, draftMode, loadNote]);

  // Sync editor value from selected note
  useEffect(() => {
    if (draftMode) return;
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

  const selectNote = useCallback((id: number) => {
    if (!draftModeRef.current && selectedNoteIdRef.current === id) {
      return;
    }
    setDraftMode(false);
    setSelectedNote(null);
    setSelectedNoteId(id);
    loadedEditorValueRef.current = "[]";
    setEditorValue("[]");
    setIsDirty(false);
  }, []);

  const createDraftPage = useCallback(() => {
    setDraftMode(true);
    setSelectedNoteId(null);
    setSelectedNote(null);
    setEditorValue("[]");
    loadedEditorValueRef.current = "[]";
    setIsDirty(false);
  }, []);

  const onEditorChange = useCallback((json: string) => {
    setEditorValue(json);
    setIsDirty(json !== loadedEditorValueRef.current);
  }, []);

  const savePage = useCallback(async (): Promise<string> => {
    if (!activeNotebookId) return "Select a notebook first.";
    if (isBlockNoteEmpty(editorValue)) return "Cannot save an empty note.";

    setBusy(true);
    try {
      if (draftModeRef.current || selectedNoteIdRef.current === null) {
        const created = await apiClient.createNote({
          notebookId: activeNotebookId,
          noteType: "generic_note/v1",
          editorContent: editorValue,
        });
        setDraftMode(false);
        setSelectedNoteId(created.id);
        setSelectedNote(created);
        loadedEditorValueRef.current = created.editorContent;
        setIsDirty(false);
        await refreshNotebook(activeNotebookId, created.id);
        return `Created "${created.title}".`;
      } else {
        const updated = await apiClient.updateNote(selectedNoteIdRef.current, {
          notebookId: activeNotebookId,
          editorContent: editorValue,
          currentRevisionId: selectedNote?.currentRevisionId ?? null,
        });
        setSelectedNote(updated);
        loadedEditorValueRef.current = updated.editorContent;
        setIsDirty(false);
        await refreshNotebook(activeNotebookId, updated.id);
        return "Note saved.";
      }
    } catch (err) {
      throw err;
    } finally {
      setBusy(false);
    }
  }, [activeNotebookId, editorValue, selectedNote?.currentRevisionId, refreshNotebook]);

  // Auto-save after 3 seconds of inactivity
  useAutoSave(() => { void savePage(); }, isDirty, 3000);

  /** Called by chat hook after AI updates a note */
  const syncUpdatedNote = useCallback(async (note: Note) => {
    const isInbox = (note.title ?? "").trim().toLowerCase() === "inbox";
    if (isInbox) {
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
  }, [activeNotebookId, refreshNotebook]);

  const clearForNotebookSwitch = useCallback(() => {
    setDraftMode(false);
    setSelectedNoteId(null);
    setSelectedNote(null);
    loadedEditorValueRef.current = "[]";
    setEditorValue("[]");
    setIsDirty(false);
  }, []);

  return {
    notes,
    selectedNoteId,
    selectedNote,
    draftMode,
    editorValue,
    editorKey,
    isDirty,
    busy,
    selectNote,
    createDraftPage,
    onEditorChange,
    savePage,
    syncUpdatedNote,
    refreshNotebook,
    clearForNotebookSwitch,
  };
}
