import { useCallback, useEffect, useState } from "react";
import { apiClient } from "../lib/apiClient";
import type { Notebook } from "../lib/types";

export function useNotebooks() {
  const [notebooks, setNotebooks] = useState<Notebook[]>([]);
  const [activeNotebookId, setActiveNotebookId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const activeNotebook = notebooks.find((nb) => nb.id === activeNotebookId) ?? null;

  const bootstrap = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const nbs = await apiClient.listNotebooks();
      setNotebooks(nbs);
      if (nbs.length > 0) {
        setActiveNotebookId(nbs[0].id);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  const selectNotebook = useCallback((id: number) => {
    setActiveNotebookId(id);
  }, []);

  const createNotebook = useCallback(async (name: string, description: string): Promise<Notebook | null> => {
    try {
      const notebook = await apiClient.createNotebook({
        name: name.trim(),
        description: description.trim() || name.trim(),
      });
      setNotebooks((prev) => [...prev, notebook]);
      setActiveNotebookId(notebook.id);
      return notebook;
    } catch (err) {
      throw err;
    }
  }, []);

  return {
    notebooks,
    activeNotebook,
    activeNotebookId,
    isLoading,
    error,
    selectNotebook,
    createNotebook,
  };
}
