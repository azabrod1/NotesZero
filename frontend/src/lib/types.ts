export interface Notebook {
  id: number;
  name: string;
  description: string;
  routingSummary?: string | null;
  createdAt: string;
}

export interface NoteDocumentMeta {
  noteId?: number | null;
  title: string;
  summaryShort: string;
  noteType: string;
  schemaVersion: string;
  notebookId?: number | null;
  currentRevisionId?: number | null;
}

export interface NoteSection {
  id: string;
  label: string;
  kind: string;
  contentMarkdown: string;
}

export interface NoteDocument {
  meta: NoteDocumentMeta;
  sections: NoteSection[];
}

export interface NoteSummary {
  id: number;
  notebookId?: number | null;
  title: string;
  summaryShort: string;
  noteType: string;
  schemaVersion: string;
  currentRevisionId?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface Note extends NoteSummary {
  notebookName?: string | null;
  editorContent: string;
  document: NoteDocument;
}

export interface RoutePlan {
  intent: "WRITE_EXISTING_NOTE" | "CREATE_NOTE" | "ANSWER_ONLY" | "CLARIFY";
  targetNotebookId?: number | null;
  targetNoteId?: number | null;
  targetNoteType: string;
  confidence: number;
  reasonCodes: string[];
  strategy: "DIRECT_APPLY" | "NOTE_INBOX" | "NOTEBOOK_INBOX" | "ANSWER_ONLY" | "CLARIFY";
  answer?: string | null;
}

export interface PatchOperation {
  op: string;
  sectionId?: string | null;
  afterSectionId?: string | null;
  title?: string | null;
  summaryShort?: string | null;
  contentMarkdown?: string | null;
}

export interface PatchPlan {
  targetNotebookId?: number | null;
  targetNoteId?: number | null;
  targetNoteType: string;
  ops: PatchOperation[];
  fallbackToInbox: boolean;
  plannerPromptVersion: string;
}

export interface ApplyResult {
  noteId: number;
  notebookId?: number | null;
  beforeRevisionId?: number | null;
  afterRevisionId: number;
  outcome: string;
  changedSectionIds: string[];
}

export interface DiffEntry {
  sectionId: string;
  label: string;
  changeType: string;
  beforeText: string;
  afterText: string;
}

export interface Provenance {
  chatEventId: number;
  providerName: string;
  routerModel: string;
  plannerModel: string;
  routerPromptVersion: string;
  plannerPromptVersion: string;
  routeConfidence: number;
  reasonCodes: string[];
}

export interface CommitChatResponse {
  chatEventId: number;
  routePlan: RoutePlan;
  patchPlan: PatchPlan;
  applyResult?: ApplyResult | null;
  updatedNote?: Note | null;
  diff: DiffEntry[];
  provenance?: Provenance | null;
  undoToken?: string | null;
  answer?: string | null;
}

export interface RevisionHistoryEntry {
  revisionId: number;
  revisionNumber: number;
  title: string;
  summaryShort: string;
  sourceChatEventId?: number | null;
  createdAt: string;
}

export interface UndoResult {
  applyResult: ApplyResult;
  updatedNote: Note;
  diff: DiffEntry[];
  undoToken: string;
}

export interface NoteMutationPayload {
  notebookId?: number | null;
  noteType?: string | null;
  title?: string | null;
  editorContent: string;
  currentRevisionId?: number | null;
}

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  createdAt: string;
  commit?: CommitChatResponse | null;
  undo?: {
    noteId: number;
    operationId: number;
  } | null;
}
