import "@blocknote/core/fonts/inter.css";
import "@blocknote/mantine/style.css";

import { Block, BlockNoteEditor, PartialBlock } from "@blocknote/core";
import { useCreateBlockNote } from "@blocknote/react";
import { BlockNoteView } from "@blocknote/mantine";
import { FileText } from "lucide-react";
import { useEffect, useRef } from "react";
import styles from "./NoteEditor.module.css";

interface NoteEditorProps {
  initialContent: string;
  onChange: (jsonString: string) => void;
  placeholder?: string;
  theme?: "light" | "dark";
}

function parseInitialContent(raw: string): PartialBlock[] | undefined {
  if (!raw || raw === "[]") return undefined;
  const trimmed = raw.trim();
  if (trimmed.startsWith("[")) {
    try {
      const blocks = JSON.parse(trimmed) as PartialBlock[];
      return blocks.length > 0 ? blocks : undefined;
    } catch {
      return undefined;
    }
  }
  // Legacy HTML will be handled after editor creation
  return undefined;
}

function isLegacyHtml(raw: string): boolean {
  if (!raw) return false;
  const trimmed = raw.trim();
  return trimmed.startsWith("<") && trimmed.includes(">");
}

export function NoteEditor({ initialContent, onChange, placeholder, theme }: NoteEditorProps) {
  const initialBlocks = parseInitialContent(initialContent);
  const needsHtmlConversion = isLegacyHtml(initialContent);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  const editor = useCreateBlockNote({
    initialContent: initialBlocks,
    domAttributes: {
      editor: {
        "data-placeholder": placeholder ?? "Start writing...",
      },
    },
  });

  // Handle legacy HTML conversion after editor is created
  useEffect(() => {
    if (needsHtmlConversion && editor) {
      try {
        const blocks = editor.tryParseHTMLToBlocks(initialContent);
        if (blocks.length > 0) {
          editor.replaceBlocks(editor.document, blocks);
        }
      } catch {
        // If HTML parsing fails, leave editor empty
      }
    }
  }, []); // Only on mount

  // Emit changes as JSON
  useEffect(() => {
    if (!editor) return;
    const handler = () => {
      const json = JSON.stringify(editor.document);
      onChangeRef.current(json);
    };
    // Use the editor's onChange subscription
    return editor.onChange(handler);
  }, [editor]);

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
      <BlockNoteView
        editor={editor}
        theme={theme ?? "dark"}
        className={styles.editor}
      />
    </div>
  );
}
