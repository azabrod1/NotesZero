import {
  CommitChatResponse,
  Note,
  NoteMutationPayload,
  Notebook,
  NoteSummary,
  RevisionHistoryEntry,
  UndoResult
} from "./types";

interface CommitChatPayload {
  message: string;
  selectedNotebookId?: number | null;
  selectedNoteId?: number | null;
  recentChatEventIds?: number[];
  currentRevisionId?: number | null;
}

const JSON_HEADERS = { "Content-Type": "application/json" };

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  const payload = await response.text();

  if (!response.ok) {
    if (payload) {
      try {
        const parsed = JSON.parse(payload) as { message?: string };
        throw new Error(parsed.message || payload);
      } catch {
        throw new Error(payload);
      }
    }
    throw new Error(`${response.status} ${response.statusText}`);
  }

  if (!payload) {
    return undefined as unknown as T;
  }

  try {
    return JSON.parse(payload) as T;
  } catch {
    throw new Error(`Server returned non-JSON payload for ${path}.`);
  }
}

export const apiClient = {
  listNotebooks(): Promise<Notebook[]> {
    return request<Notebook[]>("/api/v2/notebooks");
  },

  createNotebook(payload: { name: string; description: string }): Promise<Notebook> {
    return request<Notebook>("/api/v2/notebooks", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });
  },

  listNotes(notebookId: number): Promise<NoteSummary[]> {
    return request<NoteSummary[]>(`/api/v2/notes?notebookId=${encodeURIComponent(notebookId)}`);
  },

  getNote(noteId: number): Promise<Note> {
    return request<Note>(`/api/v2/notes/${noteId}`);
  },

  createNote(payload: NoteMutationPayload): Promise<Note> {
    return request<Note>("/api/v2/notes", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });
  },

  updateNote(noteId: number, payload: NoteMutationPayload): Promise<Note> {
    return request<Note>(`/api/v2/notes/${noteId}`, {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });
  },

  commitChat(payload: CommitChatPayload): Promise<CommitChatResponse> {
    return request<CommitChatResponse>("/api/v2/chat-events/commit", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });
  },

  history(noteId: number): Promise<RevisionHistoryEntry[]> {
    return request<RevisionHistoryEntry[]>(`/api/v2/notes/${noteId}/history`);
  },

  undo(noteId: number, operationId: number): Promise<UndoResult> {
    return request<UndoResult>(`/api/v2/notes/${noteId}/undo?operationId=${encodeURIComponent(operationId)}`, {
      method: "POST"
    });
  },

  recomputeSummary(noteId: number): Promise<Note> {
    return request<Note>(`/api/v2/notes/${noteId}/recompute-summary`, {
      method: "POST"
    });
  }
};
