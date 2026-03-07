import { useEffect, useRef } from "react";

export function useAutoSave(
  callback: () => void,
  dirty: boolean,
  delayMs: number = 2000
) {
  const savedCallback = useRef(callback);

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    if (!dirty) return;
    const timer = setTimeout(() => savedCallback.current(), delayMs);
    return () => clearTimeout(timer);
  }, [dirty, delayMs]);
}
