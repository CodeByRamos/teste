import { timeAgo } from "@/lib/format";
import type { BuildItem } from "@/lib/types";
import { ExternalIcon, SearchIcon } from "./icons";
import { IsoPart } from "./pc-diagram";
import { Price } from "./ui";

/** A price shopping search for a part, for when no connected store has an offer. */
function searchUrl(item: BuildItem) {
  return `https://www.google.com/search?tbm=shop&q=${encodeURIComponent(item.component.name)}`;
}

/**
 * "Melhores sites": for each part, the cheapest current offer among the connected stores (chosen by the backend's
 * price service). Nothing is invented: a part without a real offer says so and links to a price search instead.
 */
export function BestStores({ items }: { items: BuildItem[] }) {
  const toBuy = items.filter((item) => !item.owned);
  const realOffers = toBuy.filter((item) => item.price && !item.price.isExample && item.price.url);

  return (
    <section id="lojas" aria-labelledby="lojas-title" className="reveal panel scroll-mt-6 rounded-2xl p-5 sm:p-6">
      <h2 id="lojas-title" className="text-lg font-semibold">
        Melhores sites
      </h2>
      <p className="mt-1 text-sm text-muted">O menor preço de cada peça entre as lojas parceiras.</p>

      <ul className="mt-5 divide-y divide-border">
        {toBuy.map((item) => {
          const offer = item.price && !item.price.isExample && item.price.url ? item.price : null;
          return (
            <li key={item.category} className="flex flex-col gap-3 py-3.5 sm:flex-row sm:items-center sm:gap-4">
              <div className="flex min-w-0 flex-1 items-center gap-3">
                <IsoPart category={item.category} className="size-10 shrink-0" />
                <div className="min-w-0">
                  <p className="text-xs font-semibold tracking-wide text-accent uppercase">{item.categoryLabel}</p>
                  <p className="truncate text-sm font-medium">{item.component.name}</p>
                </div>
              </div>
              {offer ? (
                <div className="flex items-center gap-4 sm:justify-end">
                  <div className="text-left sm:text-right">
                    <p className="font-semibold tabular-nums">
                      <Price amount={offer.amountBrl} />
                    </p>
                    <p className="text-xs text-subtle">
                      {offer.storeName} · visto {timeAgo(offer.observedAt)}
                    </p>
                  </div>
                  <a
                    href={offer.url!}
                    target="_blank"
                    rel="sponsored nofollow noopener noreferrer"
                    className="inline-flex shrink-0 items-center gap-1.5 rounded-lg bg-accent-soft px-3 py-1.5 text-sm font-semibold text-accent transition-[filter] hover:brightness-125"
                  >
                    Ver na loja <ExternalIcon className="size-3.5" />
                  </a>
                </div>
              ) : (
                <a
                  href={searchUrl(item)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex shrink-0 items-center gap-1.5 self-start rounded-lg px-3 py-1.5 text-sm font-medium text-muted transition-colors hover:bg-surface-muted hover:text-foreground sm:self-center"
                >
                  <SearchIcon className="size-3.5" /> Pesquisar preço
                </a>
              )}
            </li>
          );
        })}
      </ul>

      {realOffers.length > 0 && (
        <p className="mt-3 text-xs leading-relaxed text-subtle">
          Preços podem mudar: confira na loja antes de comprar. Alguns links são de parceiros: a loja pode nos pagar uma comissão, sem
          custo extra para você.
        </p>
      )}
    </section>
  );
}
