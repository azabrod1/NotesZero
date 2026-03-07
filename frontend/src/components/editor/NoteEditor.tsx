import Highlight from "@tiptap/extension-highlight";
import Link from "@tiptap/extension-link";
import Placeholder from "@tiptap/extension-placeholder";
import TaskItem from "@tiptap/extension-task-item";
import TaskList from "@tiptap/extension-task-list";
import TextAlign from "@tiptap/extension-text-align";
import Underline from "@tiptap/extension-underline";
import { EditorContent, useEditor } from "@tiptap/react";
import { Extension } from "@tiptap/core";
import StarterKit from "@tiptap/starter-kit";
import { FileText } from "lucide-react";
import { useEffect, useRef } from "react";
import { EditorToolbar } from "./EditorToolbar";
import styles from "./NoteEditor.module.css";

const TabHandler = Extension.create({
  name: "tabHandler",
  addKeyboardShortcuts() {
    return {
      Tab: ({ editor }) => {
        if (editor.isActive("bulletList") || editor.isActive("orderedList") || editor.isActive("taskList")) {
          return editor.chain().sinkListItem("listItem").run()
            || editor.chain().sinkListItem("taskItem").run();
        }
        return true; // prevent default browser Tab behavior
      },
      "Shift-Tab": ({ editor }) => {
        if (editor.isActive("bulletList") || editor.isActive("orderedList") || editor.isActive("taskList")) {
          return editor.chain().liftListItem("listItem").run()
            || editor.chain().liftListItem("taskItem").run();
        }
        return true;
      }
    };
  }
});

interface NoteEditorProps {
  value: string;
  onChange: (html: string) => void;
  placeholder?: string;
}

export function NoteEditor({ value, onChange, placeholder }: NoteEditorProps) {
  const suppressUpdate = useRef(false);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] }
      }),
      Placeholder.configure({
        placeholder: placeholder ?? "Start writing..."
      }),
      Underline,
      Highlight.configure({ multicolor: false }),
      TaskList,
      TaskItem.configure({ nested: true }),
      TextAlign.configure({ types: ["heading", "paragraph"] }),
      Link.configure({ openOnClick: false, autolink: true }),
      TabHandler
    ],
    content: value,
    onUpdate: ({ editor: e }) => {
      if (suppressUpdate.current) return;
      onChange(e.getHTML());
    }
  });

  useEffect(() => {
    if (!editor) return;
    const current = editor.getHTML();
    if (current !== value) {
      suppressUpdate.current = true;
      editor.commands.setContent(value, false);
      suppressUpdate.current = false;
    }
  }, [value, editor]);

  if (!editor) {
    return (
      <div className={styles.emptyState}>
        <FileText size={48} />
        <span className={styles.emptyStateText}>Loading editor...</span>
      </div>
    );
  }

  return (
    <div className={styles.editorWrap}>
      <EditorToolbar editor={editor} />
      <div className={styles.editor}>
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}
