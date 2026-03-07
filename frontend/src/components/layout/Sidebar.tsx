import { BookOpen, Plus } from "lucide-react";
import type { Notebook } from "../../lib/types";
import styles from "./Sidebar.module.css";

interface SidebarProps {
  notebooks: Notebook[];
  activeNotebookId: number | null;
  collapsed: boolean;
  onSelectNotebook: (id: number) => void;
  onCreateNotebook: () => void;
}

export function Sidebar({
  notebooks,
  activeNotebookId,
  collapsed,
  onSelectNotebook,
  onCreateNotebook
}: SidebarProps) {
  return (
    <aside
      className={`${styles.sidebar} ${collapsed ? styles.sidebarCollapsed : ""}`}
    >
      <div className={styles.header}>
        <span className={styles.title}>Notebooks</span>
        <span className={styles.count}>{notebooks.length}</span>
      </div>

      <div className={styles.list}>
        {notebooks.map((nb) => {
          const isActive = nb.id === activeNotebookId;
          return (
            <button
              key={nb.id}
              className={`${styles.item} ${isActive ? styles.itemActive : ""}`}
              onClick={() => onSelectNotebook(nb.id)}
            >
              <span className={styles.accentBar} />
              <BookOpen size={16} className={styles.itemIcon} />
              <div className={styles.itemContent}>
                <div className={styles.itemName}>{nb.name}</div>
                {nb.description && (
                  <div className={styles.itemDesc}>{nb.description}</div>
                )}
              </div>
            </button>
          );
        })}
      </div>

      <div className={styles.footer}>
        <button className={styles.newBtn} onClick={onCreateNotebook}>
          <Plus size={14} />
          New Notebook
        </button>
      </div>
    </aside>
  );
}
