import { useCallback, useEffect, useState } from "react";

function query(q: string): boolean {
  if (typeof window === "undefined") return false;
  return window.matchMedia(q).matches;
}

export function useMediaQuery(mediaQuery: string): boolean {
  const [matches, setMatches] = useState(() => query(mediaQuery));

  useEffect(() => {
    const mql = window.matchMedia(mediaQuery);
    const handler = (e: MediaQueryListEvent) => setMatches(e.matches);
    mql.addEventListener("change", handler);
    setMatches(mql.matches);
    return () => mql.removeEventListener("change", handler);
  }, [mediaQuery]);

  return matches;
}

export function useBreakpoints() {
  const isMobile = useMediaQuery("(max-width: 767px)");
  const isTablet = useMediaQuery("(min-width: 768px) and (max-width: 1023px)");
  const isDesktop = useMediaQuery("(min-width: 1024px)");

  return { isMobile, isTablet, isDesktop };
}
