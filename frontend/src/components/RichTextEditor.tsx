import ReactQuill from "react-quill";

interface RichTextEditorProps {
  value: string;
  onChange: (value: string) => void;
}

const modules = {
  toolbar: [
    [{ header: [1, 2, 3, false] }],
    [{ font: [] }, { size: ["small", false, "large", "huge"] }],
    ["bold", "italic", "underline", "strike"],
    [{ color: [] }, { background: [] }],
    [{ list: "ordered" }, { list: "bullet" }, { indent: "-1" }, { indent: "+1" }],
    [{ align: [] }],
    ["link", "blockquote", "code-block", "clean"]
  ]
};

const formats = [
  "header",
  "font",
  "size",
  "bold",
  "italic",
  "underline",
  "strike",
  "color",
  "background",
  "list",
  "bullet",
  "indent",
  "align",
  "link",
  "blockquote",
  "code-block"
];

export function RichTextEditor({ value, onChange }: RichTextEditorProps) {
  return (
    <div className="editor-shell">
      <ReactQuill value={value} onChange={onChange} theme="snow" modules={modules} formats={formats} />
    </div>
  );
}
