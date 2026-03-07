import { AlertTriangle } from "lucide-react";
import type { ClarificationTask } from "../lib/types";
import styles from "./ClarificationBanner.module.css";

interface ClarificationBannerProps {
  tasks: ClarificationTask[];
  onResolve: (taskId: number, selectedOption: string) => void;
}

export function ClarificationBanner({ tasks, onResolve }: ClarificationBannerProps) {
  if (tasks.length === 0) return null;

  const task = tasks[0];

  return (
    <div className={styles.banner}>
      <AlertTriangle size={16} className={styles.icon} />
      <span className={styles.question}>{task.question}</span>
      <div className={styles.options}>
        {task.options.map((opt) => (
          <button
            key={opt}
            className={styles.pill}
            onClick={() => onResolve(task.id, opt)}
          >
            {opt}
          </button>
        ))}
      </div>
    </div>
  );
}
