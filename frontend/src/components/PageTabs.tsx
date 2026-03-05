import { Note } from "../lib/types";
import { noteTitle } from "../lib/textUtils";

interface PageTabsProps {
  notes: Note[];
  selectedNoteId: number | null;
  onSelectNote: (noteId: number) => void;
  onCreatePage: () => void;
}

export function PageTabs({ notes, selectedNoteId, onSelectNote, onCreatePage }: PageTabsProps) {
  return (
    <div className="page-tabs">
      <div className="page-tabs-scroll">
        <button
          type="button"
          className={selectedNoteId === null ? "page-tab is-active" : "page-tab"}
          onClick={onCreatePage}
        >
          + New page
        </button>

        {notes.map((note) => (
          <button
            type="button"
            key={note.id}
            className={selectedNoteId === note.id ? "page-tab is-active" : "page-tab"}
            onClick={() => onSelectNote(note.id)}
            title={noteTitle(note.rawText ?? "")}
          >
            <span>{noteTitle(note.rawText ?? "")}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
