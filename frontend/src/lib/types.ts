export type SourceType = "TEXT" | "PHOTO";
export type NoteStatus = "READY" | "NEEDS_CLARIFICATION";
export type ClarificationType = "NOTEBOOK_ASSIGNMENT" | "TEMPERATURE_UNIT";
export type FactValueType = "NUMBER" | "TEXT" | "DATETIME";
export type ChatMode = "capture" | "query" | "edit";

export interface Notebook {
  id: number;
  name: string;
  description: string;
  createdAt: string;
}

export interface Fact {
  id: number;
  keyName: string;
  valueType: FactValueType;
  valueNumber?: number | null;
  valueText?: string | null;
  valueDatetime?: string | null;
  unit?: string | null;
  confidence: number;
}

export interface ClarificationTask {
  id: number;
  noteId: number;
  type: ClarificationType;
  question: string;
  options: string[];
  createdAt: string;
}

export interface Note {
  id: number;
  notebookId?: number | null;
  notebookName?: string | null;
  rawText: string;
  sourceType: SourceType;
  status: NoteStatus;
  occurredAt?: string | null;
  createdAt: string;
  facts: Fact[];
  clarifications: ClarificationTask[];
}

export interface QueryResponse {
  answer: string;
  citedNoteIds: number[];
}

export interface NoteMutationPayload {
  rawText: string;
  notebookId?: number;
  occurredAt?: string | null;
}

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  mode: ChatMode;
  content: string;
  createdAt: string;
}
