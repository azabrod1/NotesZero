import { Menu, Moon, Sun } from "lucide-react";
import styles from "./TopBar.module.css";

interface TopBarProps {
  notebookName: string | null;
  theme: "light" | "dark";
  onToggleTheme: () => void;
  onToggleSidebar: () => void;
  statusText?: string;
  onSave: () => void;
  saveDisabled?: boolean;
  saveBusy?: boolean;
}

export function TopBar({
  notebookName,
  theme,
  onToggleTheme,
  onToggleSidebar,
  statusText,
  onSave,
  saveDisabled,
  saveBusy
}: TopBarProps) {
  return (
    <header className={styles.topbar}>
      <div className={styles.left}>
        <button
          className={styles.menuBtn}
          onClick={onToggleSidebar}
          aria-label="Toggle sidebar"
        >
          <Menu size={20} />
        </button>
        <span className={styles.brand}>
          Note<span className={styles.brandAccent}>book</span>
        </span>
      </div>

      <div className={styles.center}>
        {notebookName && (
          <span className={styles.notebookName}>{notebookName}</span>
        )}
      </div>

      <div className={styles.right}>
        {statusText && (
          <span className={styles.saveIndicator}>{statusText}</span>
        )}
        <button
          className={styles.saveBtn}
          onClick={onSave}
          disabled={saveDisabled}
          title="Save note (Ctrl+S)"
          aria-label="Save note"
        >
          {saveBusy ? "Saving..." : "Save"}
        </button>
        <button
          className={styles.iconBtn}
          onClick={onToggleTheme}
          aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
        >
          {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
        </button>
      </div>
    </header>
  );
}
