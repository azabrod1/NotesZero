import { Component, ErrorInfo, ReactNode } from "react";

interface AppErrorBoundaryProps {
  children: ReactNode;
}

interface AppErrorBoundaryState {
  hasError: boolean;
  message: string;
}

const crashStyle: React.CSSProperties = {
  minHeight: "100%",
  display: "grid",
  placeContent: "center",
  gap: 12,
  padding: 32,
  fontFamily: "var(--font-body)",
  color: "var(--text)"
};

const headingStyle: React.CSSProperties = {
  margin: 0,
  fontFamily: "var(--font-heading)",
  fontSize: "1.4rem",
  fontWeight: 700
};

const msgStyle: React.CSSProperties = {
  margin: 0,
  color: "var(--text-muted)",
  fontSize: "0.9rem"
};

const btnStyle: React.CSSProperties = {
  justifySelf: "start",
  border: "1px solid var(--border)",
  borderRadius: 8,
  background: "var(--surface)",
  color: "var(--text)",
  padding: "8px 16px",
  cursor: "pointer",
  fontWeight: 600
};

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  public constructor(props: AppErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, message: "" };
  }

  public static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return {
      hasError: true,
      message: error.message || "Unexpected runtime error."
    };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("App render failure", error, errorInfo);
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div style={crashStyle}>
          <h1 style={headingStyle}>Something went wrong</h1>
          <p style={msgStyle}>{this.state.message}</p>
          <button type="button" style={btnStyle} onClick={() => window.location.reload()}>
            Reload
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
