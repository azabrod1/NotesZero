import "@blocknote/core/fonts/inter.css";
import "@blocknote/mantine/style.css";

import { PartialBlock } from "@blocknote/core";
import { useCreateBlockNote } from "@blocknote/react";
import { BlockNoteView } from "@blocknote/mantine";
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

function blankParagraph(): PartialBlock[] {
  return [{ type: "paragraph", content: [] }];
}

export function NoteEditor({ initialContent, onChange, placeholder, theme }: NoteEditorProps) {
  const initialBlocks = parseInitialContent(initialContent);
  const onChangeRef = useRef(onChange);
  const applyingIncomingContentRef = useRef(false);
  onChangeRef.current = onChange;

  const editor = useCreateBlockNote({
    initialContent: initialBlocks,
    domAttributes: {
      editor: {
        "data-placeholder": placeholder ?? "Start writing...",
      },
    },
  });

  useEffect(() => {
    if (!editor) {
      return;
    }
    try {
      const nextBlocks = isLegacyHtml(initialContent)
        ? editor.tryParseHTMLToBlocks(initialContent)
        : (parseInitialContent(initialContent) ?? blankParagraph());
      const currentJson = JSON.stringify(editor.document);
      const nextJson = JSON.stringify(nextBlocks);
      if (currentJson === nextJson) {
        return;
      }
      applyingIncomingContentRef.current = true;
      editor.replaceBlocks(editor.document, nextBlocks);
    } catch {
      // Ignore invalid payloads and keep the current document.
    } finally {
      try {
        queueMicrotask(() => {
          applyingIncomingContentRef.current = false;
        });
      } catch {
        applyingIncomingContentRef.current = false;
      }
    }
  }, [editor, initialContent]);

  // Emit changes as JSON
  useEffect(() => {
    if (!editor) return;
    const handler = () => {
      if (applyingIncomingContentRef.current) {
        return;
      }
      const json = JSON.stringify(editor.document);
      onChangeRef.current(json);
    };
    // Use the editor's onChange subscription
    return editor.onChange(handler);
  }, [editor]);

  if (!editor) {
    return (
      <div className={styles.skeleton}>
        <div className={styles.skeletonBar} style={{ width: "70%" }} />
        <div className={styles.skeletonBar} style={{ width: "100%" }} />
        <div className={styles.skeletonBar} style={{ width: "85%" }} />
        <div className={styles.skeletonBar} style={{ width: "60%" }} />
      </div>
    );
  }

  return (
    <div className={styles.editorWrap} data-testid="note-editor">
      <BlockNoteView
        editor={editor}
        theme={theme ?? "dark"}
        className={styles.editor}
      />
    </div>
  );
}
