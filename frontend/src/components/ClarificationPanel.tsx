import { ClarificationTask } from "../lib/types";

interface ClarificationPanelProps {
  tasks: ClarificationTask[];
  onResolve: (taskId: number, selectedOption: string) => void;
}

export function ClarificationPanel({ tasks, onResolve }: ClarificationPanelProps) {
  if (!tasks.length) {
    return null;
  }

  return (
    <section className="clarification-panel">
      <header>
        <h3>Needs Clarification</h3>
        <span>{tasks.length}</span>
      </header>

      <div className="clarification-list">
        {tasks.slice(0, 4).map((task) => (
          <article key={task.id} className="clarification-card">
            <p>{task.question}</p>
            <div className="option-row">
              {(task.options ?? []).map((option) => (
                <button
                  key={option}
                  className="option-pill"
                  type="button"
                  onClick={() => onResolve(task.id, option)}
                >
                  {option}
                </button>
              ))}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
