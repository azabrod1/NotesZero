import { useCallback, useState } from "react";
import { apiClient } from "../lib/apiClient";
import type { ChatMessage, CommitChatResponse, Note } from "../lib/types";

function msgId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function isHiddenInboxCommit(result: CommitChatResponse): boolean {
  const title = result.updatedNote?.title ?? "";
  return Boolean(result.patchPlan.fallbackToInbox && title.trim().toLowerCase() === "inbox");
}

function assistantSummary(result: CommitChatResponse): string {
  if (result.answer) return result.answer;
  if (!result.updatedNote || !result.applyResult) return "No note changes were applied.";
  if (isHiddenInboxCommit(result)) {
    return `Queued this in ${result.updatedNote.notebookName ?? "the selected notebook"} for later organization.`;
  }
  if (result.patchPlan.fallbackToInbox) {
    return `Queued this in "${result.updatedNote.title}" for later organization.`;
  }
  const count = result.applyResult.changedSectionIds.length;
  const text = count === 1 ? "1 section" : `${count} sections`;
  return `Updated "${result.updatedNote.title}" in ${result.updatedNote.notebookName ?? "the selected notebook"} and changed ${text}.`;
}

const WELCOME_MESSAGE: ChatMessage = {
  id: "welcome",
  role: "assistant",
  content: "Send a thought, edit request, or question. I'll route it to the right note, patch the content, and keep an undo trail.",
  createdAt: new Date().toISOString(),
};

interface UseChatOptions {
  activeNotebookId: number | null;
  selectedNoteId: number | null;
  selectedNoteRevisionId: number | null;
  draftMode: boolean;
  syncUpdatedNote: (note: Note) => Promise<void>;
  setActiveNotebookId: (id: number) => void;
  refreshNotebook: (notebookId: number, preferredNoteId?: number | null) => Promise<void>;
}

export function useChat(options: UseChatOptions) {
  const [messages, setMessages] = useState<ChatMessage[]>([WELCOME_MESSAGE]);
  const [busy, setBusy] = useState(false);

  const pushMessage = useCallback((msg: ChatMessage) => {
    setMessages((prev) => [...prev, msg]);
  }, []);

  const recentChatEventIds = useCallback((): number[] => {
    return messages
      .map((m) => m.commit?.chatEventId ?? null)
      .filter((v): v is number => v !== null)
      .slice(-3);
  }, [messages]);

  const submit = useCallback(async (text: string) => {
    pushMessage({
      id: msgId(),
      role: "user",
      content: text,
      createdAt: new Date().toISOString(),
    });

    if (options.activeNotebookId === null) {
      pushMessage({
        id: msgId(),
        role: "assistant",
        content: "Select a notebook first.",
        createdAt: new Date().toISOString(),
      });
      return;
    }

    setBusy(true);
    try {
      const result = await apiClient.commitChat({
        message: text,
        selectedNotebookId: options.activeNotebookId,
        selectedNoteId: options.draftMode ? null : options.selectedNoteId,
        recentChatEventIds: recentChatEventIds(),
        currentRevisionId: options.draftMode ? null : options.selectedNoteRevisionId,
      });

      if (result.updatedNote) {
        if (result.updatedNote.notebookId) {
          options.setActiveNotebookId(result.updatedNote.notebookId);
          await options.refreshNotebook(result.updatedNote.notebookId, result.updatedNote.id);
        }
        await options.syncUpdatedNote(result.updatedNote);
      }

      pushMessage({
        id: msgId(),
        role: "assistant",
        content: assistantSummary(result),
        createdAt: new Date().toISOString(),
        commit: result,
        undo: result.updatedNote && result.undoToken
          ? { noteId: result.updatedNote.id, operationId: Number(result.undoToken) }
          : null,
      });
    } catch (err) {
      pushMessage({
        id: msgId(),
        role: "assistant",
        content: `Request failed: ${(err as Error).message}`,
        createdAt: new Date().toISOString(),
      });
    } finally {
      setBusy(false);
    }
  }, [options, pushMessage, recentChatEventIds]);

  const undo = useCallback(async (noteId: number, operationId: number) => {
    setBusy(true);
    try {
      const result = await apiClient.undo(noteId, operationId);
      await options.syncUpdatedNote(result.updatedNote);
      pushMessage({
        id: msgId(),
        role: "assistant",
        content: `Undo restored the previous revision of "${result.updatedNote.title}".`,
        createdAt: new Date().toISOString(),
      });
    } catch (err) {
      pushMessage({
        id: msgId(),
        role: "assistant",
        content: `Undo failed: ${(err as Error).message}`,
        createdAt: new Date().toISOString(),
      });
    } finally {
      setBusy(false);
    }
  }, [options, pushMessage]);

  return { messages, busy, submit, undo };
}
