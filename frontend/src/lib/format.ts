const brlFormatter = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const brlShortFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
  maximumFractionDigits: 0,
});

export function brl(value: number) {
  return brlFormatter.format(value);
}

export function brlShort(value: number) {
  return brlShortFormatter.format(value);
}

export function signedBrl(value: number) {
  const formatted = brlFormatter.format(Math.abs(value));
  return value < 0 ? `− ${formatted}` : `+ ${formatted}`;
}

export function dateTime(iso: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long", timeStyle: "short" }).format(new Date(iso));
}
