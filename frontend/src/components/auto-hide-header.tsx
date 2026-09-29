"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";

/** Scrolling less than this (in px) in one direction does not toggle the header, so small jitters are ignored. */
const THRESHOLD = 8;

/**
 * Sticky header that slides away while the page scrolls down and comes back as soon as it scrolls up.
 * It always shows near the top of the page and whenever keyboard focus moves into it.
 */
export function AutoHideHeader({ className, children }: { className: string; children: ReactNode }) {
  const [hidden, setHidden] = useState(false);
  const ref = useRef<HTMLElement>(null);

  useEffect(() => {
    let lastY = window.scrollY;
    let frame = 0;
    const update = () => {
      frame = 0;
      const y = window.scrollY;
      const height = ref.current?.offsetHeight ?? 64;
      if (y <= height) setHidden(false);
      else if (y - lastY > THRESHOLD) setHidden(true);
      else if (lastY - y > THRESHOLD) setHidden(false);
      else return;
      lastY = y;
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <header
      ref={ref}
      onFocus={() => setHidden(false)}
      className={`${className} transition-transform duration-300 ease-out ${hidden ? "-translate-y-full" : "translate-y-0"}`}
    >
      {children}
    </header>
  );
}
