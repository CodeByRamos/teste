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

/** "há 3 horas", "há 2 dias": how old a store price is. */
export function timeAgo(iso: string, now: number = Date.now()) {
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
  if (minutes < 60) return minutes <= 1 ? "agora há pouco" : `há ${minutes} minutos`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return hours === 1 ? "há 1 hora" : `há ${hours} horas`;
  const days = Math.round(hours / 24);
  return days === 1 ? "há 1 dia" : `há ${days} dias`;
}

export function dateTime(iso: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long", timeStyle: "short" }).format(new Date(iso));
}
