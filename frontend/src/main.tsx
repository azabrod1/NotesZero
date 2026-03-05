import React from "react";
import ReactDOM from "react-dom/client";
import "react-quill/dist/quill.snow.css";
import { App } from "./app/App";
import { AppErrorBoundary } from "./components/AppErrorBoundary";

const container = document.getElementById("root");
if (!container) {
  throw new Error("Root container #root was not found.");
}

ReactDOM.createRoot(container).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  </React.StrictMode>,
);
