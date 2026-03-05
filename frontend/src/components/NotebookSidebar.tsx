import { Notebook } from "../lib/types";

interface NotebookSidebarProps {
  notebooks: Notebook[];
  activeNotebookId: number | null;
  onSelectNotebook: (notebookId: number) => void;
}

export function NotebookSidebar({
  notebooks,
  activeNotebookId,
  onSelectNotebook
}: NotebookSidebarProps) {
  return (
    <aside className="notebook-sidebar">
      <div className="sidebar-header">
        <h2>Notebooks</h2>
        <span>{notebooks.length}</span>
      </div>

      <div className="notebook-list">
        {notebooks.map((notebook) => (
          <button
            key={notebook.id}
            className={activeNotebookId === notebook.id ? "notebook-chip is-active" : "notebook-chip"}
            onClick={() => onSelectNotebook(notebook.id)}
            type="button"
          >
            <strong>{notebook.name}</strong>
            <small>{notebook.description ?? "No description"}</small>
          </button>
        ))}
      </div>
    </aside>
  );
}
