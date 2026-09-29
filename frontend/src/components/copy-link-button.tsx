"use client";

import { useState } from "react";
import { CheckIcon, CopyIcon } from "./icons";
import { Button } from "./ui";

export function CopyLinkButton() {
  const [copied, setCopied] = useState(false);
  return (
    <Button
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(window.location.href);
          setCopied(true);
          setTimeout(() => setCopied(false), 2000);
        } catch {
          // Clipboard can be blocked; the address bar still has the link.
        }
      }}
    >
      {copied ? "Link copiado" : "Copiar link"}
    </Button>
  );
}

/** Small text button that copies a block of text (e.g. the spec sheet) and confirms for two seconds. */
export function CopyTextButton({ text, label = "Copiar" }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setCopied(true);
          setTimeout(() => setCopied(false), 2000);
        } catch {
          // Clipboard can be blocked; the text stays selectable on the page.
        }
      }}
      className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-sm font-medium text-muted transition-colors hover:bg-surface-muted hover:text-foreground active:scale-[0.98]"
      aria-live="polite"
    >
      {copied ? <CheckIcon className="size-3.5 text-ok" strokeWidth={2.5} /> : <CopyIcon className="size-3.5" />}
      {copied ? "Copiado" : label}
    </button>
  );
}
