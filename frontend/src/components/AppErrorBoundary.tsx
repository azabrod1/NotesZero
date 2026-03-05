import { Component, ErrorInfo, ReactNode } from "react";

interface AppErrorBoundaryProps {
  children: ReactNode;
}

interface AppErrorBoundaryState {
  hasError: boolean;
  message: string;
}

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
        <div className="app-crash">
          <h1>Frontend runtime error</h1>
          <p>{this.state.message}</p>
          <button type="button" onClick={() => window.location.reload()}>
            Reload
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
