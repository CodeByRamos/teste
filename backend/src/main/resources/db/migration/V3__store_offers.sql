-- Real prices from store feeds (affiliate product feeds). Separate from specifications, replaced per store on
-- every import. Only offers that passed validation are stored; rejections are counted in the import log.

create table price_feed_import (
    id            bigserial   primary key,
    store_id      text        not null,
    imported_at   timestamptz not null default now(),
    rows_read     integer     not null,
    rows_matched  integer     not null,
    rows_accepted integer     not null,
    rejections    jsonb       not null default '{}'::jsonb   -- reason -> count
);

create table store_offer (
    component_id uuid          not null references hardware_component (id) on delete cascade,
    store_id     text          not null,
    store_name   text          not null,
    price_brl    numeric(12,2) not null check (price_brl > 0),
    url          text          not null,
    availability text          not null check (availability in ('IN_STOCK', 'OUT_OF_STOCK', 'UNKNOWN')),
    observed_at  timestamptz   not null,
    import_id    bigint        not null references price_feed_import (id),
    primary key (component_id, store_id)
);

create index store_offer_store on store_offer (store_id);
