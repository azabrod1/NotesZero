import type { Editor } from "@tiptap/react";
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  Bold,
  Code,
  Heading1,
  Heading2,
  Heading3,
  Highlighter,
  Italic,
  Link,
  List,
  ListChecks,
  ListOrdered,
  Minus,
  Quote,
  Strikethrough,
  Underline
} from "lucide-react";
import styles from "./EditorToolbar.module.css";

interface EditorToolbarProps {
  editor: Editor | null;
}

export function EditorToolbar({ editor }: EditorToolbarProps) {
  if (!editor) return null;

  const btn = (
    active: boolean,
    onClick: () => void,
    icon: React.ReactNode,
    label: string,
    className?: string
  ) => (
    <button
      type="button"
      className={`${styles.btn} ${className ?? ""} ${active ? styles.btnActive : ""}`}
      onClick={onClick}
      aria-label={label}
      title={label}
    >
      {icon}
    </button>
  );

  const setLink = () => {
    const prev = editor.getAttributes("link").href ?? "";
    const url = window.prompt("URL", prev);
    if (url === null) return;
    if (url === "") {
      editor.chain().focus().extendMarkRange("link").unsetLink().run();
    } else {
      editor.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
    }
  };

  return (
    <div className={styles.toolbar}>
      <div className={styles.group}>
        {btn(editor.isActive("bold"), () => editor.chain().focus().toggleBold().run(), <Bold size={15} />, "Bold")}
        {btn(editor.isActive("italic"), () => editor.chain().focus().toggleItalic().run(), <Italic size={15} />, "Italic")}
        {btn(editor.isActive("underline"), () => editor.chain().focus().toggleUnderline().run(), <Underline size={15} />, "Underline")}
        {btn(editor.isActive("strike"), () => editor.chain().focus().toggleStrike().run(), <Strikethrough size={15} />, "Strikethrough")}
        {btn(editor.isActive("code"), () => editor.chain().focus().toggleCode().run(), <Code size={15} />, "Code")}
      </div>

      <span className={styles.divider} />

      <div className={styles.group}>
        {btn(
          editor.isActive("heading", { level: 1 }),
          () => editor.chain().focus().toggleHeading({ level: 1 }).run(),
          <Heading1 size={15} />,
          "Heading 1",
          styles.headingBtn
        )}
        {btn(
          editor.isActive("heading", { level: 2 }),
          () => editor.chain().focus().toggleHeading({ level: 2 }).run(),
          <Heading2 size={15} />,
          "Heading 2",
          styles.headingBtn
        )}
        {btn(
          editor.isActive("heading", { level: 3 }),
          () => editor.chain().focus().toggleHeading({ level: 3 }).run(),
          <Heading3 size={15} />,
          "Heading 3",
          styles.headingBtn
        )}
      </div>

      <span className={styles.divider} />

      <div className={styles.group}>
        {btn(editor.isActive("bulletList"), () => editor.chain().focus().toggleBulletList().run(), <List size={15} />, "Bullet list")}
        {btn(editor.isActive("orderedList"), () => editor.chain().focus().toggleOrderedList().run(), <ListOrdered size={15} />, "Ordered list")}
        {btn(editor.isActive("taskList"), () => editor.chain().focus().toggleTaskList().run(), <ListChecks size={15} />, "Task list")}
      </div>

      <span className={styles.divider} />

      <div className={styles.group}>
        {btn(editor.isActive("blockquote"), () => editor.chain().focus().toggleBlockquote().run(), <Quote size={15} />, "Blockquote")}
        {btn(false, () => editor.chain().focus().setHorizontalRule().run(), <Minus size={15} />, "Horizontal rule")}
        {btn(editor.isActive("highlight"), () => editor.chain().focus().toggleHighlight().run(), <Highlighter size={15} />, "Highlight")}
        {btn(editor.isActive("link"), setLink, <Link size={15} />, "Link")}
      </div>

      <span className={styles.divider} />

      <div className={styles.group}>
        {btn(editor.isActive({ textAlign: "left" }), () => editor.chain().focus().setTextAlign("left").run(), <AlignLeft size={15} />, "Align left")}
        {btn(editor.isActive({ textAlign: "center" }), () => editor.chain().focus().setTextAlign("center").run(), <AlignCenter size={15} />, "Align center")}
        {btn(editor.isActive({ textAlign: "right" }), () => editor.chain().focus().setTextAlign("right").run(), <AlignRight size={15} />, "Align right")}
      </div>
    </div>
  );
}
