-- Price bot: crawl state per store page, and price history for comparisons over time.

create table crawl_page (
    store_id        text        not null,
    url             text        not null,
    discovered_at   timestamptz not null default now(),
    last_fetched_at timestamptz,
    next_fetch_at   timestamptz not null default now(),
    etag            text,
    last_modified   text,
    -- pending: never visited; matched: product in our catalog; unmatched: product we do not carry;
    -- not_product: page without product data; disallowed: robots.txt forbids it; error: last visit failed
    status          text        not null default 'pending'
                    check (status in ('pending', 'matched', 'unmatched', 'not_product', 'disallowed', 'error')),
    component_id    uuid        references hardware_component (id) on delete set null,
    failures        integer     not null default 0,
    primary key (store_id, url)
);

create index crawl_page_due on crawl_page (store_id, next_fetch_at);

-- One row per price seen (feeds and bot), so the site can show the lowest recent price and trends.
create table price_observation (
    id           bigserial     primary key,
    component_id uuid          not null references hardware_component (id) on delete cascade,
    store_id     text          not null,
    store_name   text          not null,
    price_brl    numeric(12,2) not null check (price_brl > 0),
    availability text          not null,
    observed_at  timestamptz   not null
);

create index price_observation_component on price_observation (component_id, observed_at desc);

-- Offers found by the bot are written one at a time, not as part of a feed import.
alter table store_offer alter column import_id drop not null;
