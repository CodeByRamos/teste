"use client";

import { useState } from "react";
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
