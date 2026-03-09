import {
  ClarificationTask,
  Note,
  NoteMutationPayload,
  Notebook,
  QueryResponse,
  SourceType
} from "./types";

interface CreateNotePayload extends NoteMutationPayload {
  sourceType: SourceType;
}

interface ResolveClarificationPayload {
  selectedOption: string;
}

interface AskPayload {
  notebookId: number;
  question: string;
}

const JSON_HEADERS = { "Content-Type": "application/json" };

const RAW_API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string | undefined;
const API_BASE_URL = RAW_API_BASE_URL ? RAW_API_BASE_URL.replace(/\/$/, "") : "";

function buildUrl(path: string): string {
  return API_BASE_URL ? `${API_BASE_URL}${path}` : path;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(buildUrl(path), init);
  const payload = await response.text();

  if (!response.ok) {
    throw new Error(payload || `${response.status} ${response.statusText}`);
  }

  if (!payload) {
    return undefined as unknown as T;
  }

  try {
    return JSON.parse(payload) as T;
  } catch (error) {
    throw new Error(
      `Server returned non-JSON payload for ${path}. ` +
        "Check that the API is running and reachable from this site."
    );
  }
}

export const apiClient = {
  listNotebooks(): Promise<Notebook[]> {
    return request<Notebook[]>("/api/v1/notebooks");
  },

  createNotebook(payload: { name: string; description: string }): Promise<Notebook> {
    return request<Notebook>("/api/v1/notebooks", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });
  },

  listNotes(notebookId: number): Promise<Note[]> {
    return request<Note[]>(`/api/v1/notes?notebookId=${encodeURIComponent(notebookId)}`);
  },

  createNote(payload: CreateNotePayload): Promise<Note> {
    return request<Note>("/api/v1/notes", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });
  },

  updateNote(noteId: number, payload: NoteMutationPayload): Promise<Note> {
    return request<Note>(`/api/v1/notes/${noteId}`, {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });
  },

  listClarifications(): Promise<ClarificationTask[]> {
    return request<ClarificationTask[]>("/api/v1/clarifications");
  },

  resolveClarification(taskId: number, payload: ResolveClarificationPayload): Promise<Note> {
    return request<Note>(`/api/v1/clarifications/${taskId}/resolve`, {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });
  },

  askNotebook(payload: AskPayload): Promise<QueryResponse> {
    return request<QueryResponse>("/api/v1/query", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });
  }
};
