import { useCallback, useRef, useState } from "react";
import type { Notebook } from "../../lib/types";
import styles from "./CreateNotebookModal.module.css";

interface CreateNotebookModalProps {
  opened: boolean;
  onClose: () => void;
  onCreate: (name: string, description: string) => Promise<Notebook | null>;
}

export function CreateNotebookModal({ opened, onClose, onCreate }: CreateNotebookModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nameRef = useRef<HTMLInputElement>(null);

  const reset = useCallback(() => {
    setName("");
    setDescription("");
    setError(null);
    setBusy(false);
  }, []);

  const handleClose = useCallback(() => {
    reset();
    onClose();
  }, [onClose, reset]);

  const handleSubmit = useCallback(async () => {
    if (!name.trim()) {
      setError("Name is required.");
      nameRef.current?.focus();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onCreate(name, description);
      reset();
      onClose();
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }, [name, description, onCreate, onClose, reset]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        void handleSubmit();
      }
      if (e.key === "Escape") {
        handleClose();
      }
    },
    [handleSubmit, handleClose]
  );

  if (!opened) return null;

  return (
    <div className={styles.backdrop} onClick={handleClose}>
      <div
        className={styles.modal}
        onClick={(e) => e.stopPropagation()}
        onKeyDown={handleKeyDown}
        role="dialog"
        aria-label="Create notebook"
      >
        <h2 className={styles.title}>New Notebook</h2>

        <label className={styles.label}>
          Name
          <input
            ref={nameRef}
            className={styles.input}
            type="text"
            placeholder="My Notebook"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoFocus
            disabled={busy}
          />
        </label>

        <label className={styles.label}>
          Description
          <input
            className={styles.input}
            type="text"
            placeholder="What's this notebook for?"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            disabled={busy}
          />
        </label>

        {error && <p className={styles.error}>{error}</p>}

        <div className={styles.actions}>
          <button className={styles.cancelBtn} onClick={handleClose} disabled={busy}>
            Cancel
          </button>
          <button className={styles.createBtn} onClick={() => void handleSubmit()} disabled={busy}>
            {busy ? "Creating..." : "Create"}
          </button>
        </div>
      </div>
    </div>
  );
}
