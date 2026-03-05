import { useEffect, useMemo, useState } from "react";

type Theme = "light" | "dark";

const STORAGE_KEY = "notes-app-theme";

function resolveInitialTheme(): Theme {
  try {
    const fromStorage = window.localStorage.getItem(STORAGE_KEY);
    if (fromStorage === "light" || fromStorage === "dark") {
      return fromStorage;
    }
  } catch {
    // Continue with system preference if storage is unavailable.
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(resolveInitialTheme);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    try {
      window.localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Ignore storage write errors.
    }
  }, [theme]);

  const toggleTheme = useMemo(
    () => () => setTheme((current) => (current === "light" ? "dark" : "light")),
    []
  );

  return { theme, toggleTheme };
}
