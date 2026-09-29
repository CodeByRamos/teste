import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

function Icon({ children, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      {...props}
    >
      {children}
    </svg>
  );
}

export const CheckIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M20 6 9 17l-5-5" />
  </Icon>
);

export const AlertIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 9v4M12 17h.01" />
    <path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" />
  </Icon>
);

export const XIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M18 6 6 18M6 6l12 12" />
  </Icon>
);

export const InfoIcon = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="12" cy="12" r="10" />
    <path d="M12 16v-4M12 8h.01" />
  </Icon>
);

export const ChevronIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="m9 18 6-6-6-6" />
  </Icon>
);

export const ArrowIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M5 12h14M13 5l7 7-7 7" />
  </Icon>
);

export const ExternalIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M15 3h6v6M10 14 21 3M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
  </Icon>
);

export const SparkIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M5.6 18.4l2.8-2.8M15.6 8.4l2.8-2.8" />
  </Icon>
);

export const BoltIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M13 2 4 14h7l-1 8 9-12h-7l1-8Z" />
  </Icon>
);

/** Piggy bank: spending less. */
export const PiggyIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M19 10.5c.8.3 1.5 1 1.5 2 0 .5-.2.9-.5 1.2" />
    <path d="M5 11.5C5 8.5 8 6 12 6c1.2 0 2.3.2 3.3.6L18 5v3.2c.6.7 1 1.6 1 2.6v.7l1.5.5v2.5l-1.8.6c-.5.9-1.2 1.6-2.2 2.1V20h-2.5v-1.5a9 9 0 0 1-3 0V20H8.5v-2.3C6.4 16.6 5 14.2 5 11.5Z" />
    <path d="M9.5 8.8c.8-.3 1.6-.4 2.5-.4" />
    <circle cx="15.5" cy="10" r=".6" fill="currentColor" />
    <path d="M5 11.5c-1.2 0-2-.8-2-2" />
  </Icon>
);

/** Rocket: spending more for more performance. */
export const RocketIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 15.5 8.5 12c1.8-5 5.2-8.3 11.5-9-.7 6.3-4 9.7-8 12.5Z" />
    <circle cx="15" cy="9" r="1.6" />
    <path d="M8.5 12 5 11.5l2.5-3.5 4 .2M12 15.5l.5 3.5 3.5-2.5-.2-4" />
    <path d="M6.5 16.5c-1.2.3-2 1.5-2.5 3.5 2-.5 3.2-1.3 3.5-2.5" />
  </Icon>
);

export const SearchIcon = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="11" cy="11" r="7" />
    <path d="m21 21-4.3-4.3" />
  </Icon>
);

export const PlusIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M12 5v14M5 12h14" />
  </Icon>
);

export const WrenchIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4Z" />
  </Icon>
);

export const MonitorIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect x="2" y="3" width="20" height="14" rx="2" />
    <path d="M8 21h8M12 17v4" />
  </Icon>
);

export const CalendarIcon = (p: IconProps) => (
  <Icon {...p}>
    <rect x="3" y="4" width="18" height="18" rx="2" />
    <path d="M16 2v4M8 2v4M3 10h18" />
  </Icon>
);

export const BoxIcon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M21 8 12 3 3 8v8l9 5 9-5Z" />
    <path d="m3 8 9 5 9-5M12 13v8" />
  </Icon>
);
