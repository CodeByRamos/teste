import type { SVGProps } from "react";
import type { Category } from "@/lib/types";

type IconProps = SVGProps<SVGSVGElement>;

function Icon({ children, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      {...props}
    >
      {children}
    </svg>
  );
}

/** Line illustrations of each kind of part, drawn on the same 24 px grid as the other icons. */
const PART_ICONS: Record<Category, (props: IconProps) => React.JSX.Element> = {
  // Chip with pins on all four sides.
  CPU: (p) => (
    <Icon {...p}>
      <rect x="6" y="6" width="12" height="12" rx="1.5" />
      <rect x="9.5" y="9.5" width="5" height="5" rx="0.5" />
      <path d="M9 3v3M12 3v3M15 3v3M9 18v3M12 18v3M15 18v3M3 9h3M3 12h3M3 15h3M18 9h3M18 12h3M18 15h3" />
    </Icon>
  ),
  // Long card with two fans and the slot bracket.
  GPU: (p) => (
    <Icon {...p}>
      <rect x="2" y="6" width="20" height="11" rx="1.5" />
      <circle cx="8" cy="11.5" r="3" />
      <circle cx="16" cy="11.5" r="3" />
      <path d="M8 10v3M6.7 11.5h2.6M16 10v3M14.7 11.5h2.6M5 17v2.5h6V17" />
    </Icon>
  ),
  // Board with the CPU socket, memory slots and an expansion slot.
  MOTHERBOARD: (p) => (
    <Icon {...p}>
      <rect x="3" y="3" width="18" height="18" rx="2" />
      <rect x="6" y="6" width="6" height="6" rx="0.5" />
      <path d="M15 6v6M18 6v6M6 16h12M6 18.5h6" />
    </Icon>
  ),
  // Memory stick with its chips and contact pins.
  MEMORY: (p) => (
    <Icon {...p}>
      <path d="M2 7h20v9H13l-1 1.5-1-1.5H2z" />
      <rect x="4.5" y="9.5" width="3" height="3.5" rx="0.4" />
      <rect x="10.5" y="9.5" width="3" height="3.5" rx="0.4" />
      <rect x="16.5" y="9.5" width="3" height="3.5" rx="0.4" />
      <path d="M4 16v2.5M7 16v2.5M17 16v2.5M20 16v2.5" />
    </Icon>
  ),
  // M.2 drive: a slim board with a controller chip and the mounting screw.
  STORAGE: (p) => (
    <Icon {...p}>
      <rect x="2" y="8" width="20" height="8" rx="1.2" />
      <rect x="5" y="10" width="5" height="4" rx="0.4" />
      <rect x="12" y="10" width="4" height="4" rx="0.4" />
      <circle cx="19.5" cy="12" r="1" />
      <path d="M2 10v4" />
    </Icon>
  ),
  // Power supply box with its fan and a bolt.
  POWER_SUPPLY: (p) => (
    <Icon {...p}>
      <rect x="2" y="5" width="20" height="14" rx="1.5" />
      <circle cx="9" cy="12" r="4.5" />
      <path d="M9 7.5v9M4.5 12h9M17.5 8.5l-2 3.5h3l-2 3.5" />
    </Icon>
  ),
  // Tower case with the power button, drive bay and vents.
  CASE: (p) => (
    <Icon {...p}>
      <rect x="6" y="2" width="12" height="20" rx="1.5" />
      <circle cx="12" cy="5.5" r="1" />
      <path d="M9 9h6M9 14h6M9 16.5h6M9 19h6" />
    </Icon>
  ),
  // Fan seen from the front.
  CPU_COOLER: (p) => (
    <Icon {...p}>
      <rect x="2.5" y="2.5" width="19" height="19" rx="3" />
      <circle cx="12" cy="12" r="1.8" />
      <path d="M12 10.2c-.5-2.6.6-4.6 3-5.2M13.8 12c2.6-.5 4.6.6 5.2 3M12 13.8c.5 2.6-.6 4.6-3 5.2M10.2 12c-2.6.5-4.6-.6-5.2-3" />
    </Icon>
  ),
};

export function PartIcon({ category, ...props }: IconProps & { category: Category }) {
  const Drawing = PART_ICONS[category];
  return <Drawing {...props} />;
}
