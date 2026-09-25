-- Hardware catalog derived from external sources (currently BuildCores OpenDB, ODC-By 1.0).
-- Everything in these tables can be rebuilt from a pinned snapshot; nothing here is edited by hand.

create table source_snapshot (
    id              bigserial primary key,
    source          text        not null,
    source_version  text        not null,   -- upstream revision, e.g. git commit
    source_url      text        not null,
    license         text        not null,
    committed_at    timestamptz,
    ingested_at     timestamptz not null default now(),
    record_count    integer     not null,
    quality_summary jsonb       not null,
    unique (source, source_version)
);

create table hardware_component (
    id             uuid        primary key,           -- deterministic from (source, external_id)
    source         text        not null,
    external_id    text        not null,              -- identifier in the source, preserved verbatim
    category       text        not null,
    name           text        not null,
    manufacturer   text,
    series         text,
    variant        text,
    release_year   integer,
    specs          jsonb       not null,              -- normalized domain projection used by the engines
    raw            jsonb       not null,              -- original source record, untouched
    quality_score  real        not null,
    quality_issues jsonb       not null,
    status         text        not null default 'active' check (status in ('active', 'removed_upstream')),
    snapshot_id    bigint      not null references source_snapshot (id),
    first_seen_at  timestamptz not null default now(),
    last_seen_at   timestamptz not null default now(),
    unique (source, external_id)
);

create index hardware_component_active_category on hardware_component (category) where status = 'active';

-- EAN/UPC/GTIN/MPN codes, used to match store listings to catalog parts.
create table component_identifier (
    component_id uuid not null references hardware_component (id) on delete cascade,
    type         text not null check (type in ('ean', 'upc', 'gtin', 'mpn')),
    value        text not null,
    primary key (component_id, type, value)
);

create index component_identifier_value on component_identifier (value);

-- Curated corrections to source data. Never overwrite source rows; record the fix and why.
create table component_override (
    id           bigserial primary key,
    component_id uuid        not null references hardware_component (id),
    field        text        not null,
    value        jsonb       not null,
    reason       text        not null,
    author       text        not null,
    created_at   timestamptz not null default now()
);

-- Builds people chose to keep. The id is random and acts as the access key until accounts exist.
create table saved_build (
    id                  uuid          primary key,
    created_at          timestamptz   not null default now(),
    title               text,
    request             jsonb,
    total_brl           numeric(12, 2) not null,
    prices_are_examples boolean       not null,
    engine_version      text          not null,
    catalog_version     text          not null,
    view                jsonb         not null        -- exactly what was shown when saved
);

create table saved_build_item (
    build_id     uuid          not null references saved_build (id) on delete cascade,
    position     integer       not null,
    component_id uuid          not null,             -- no FK: the part may later be removed upstream
    category     text          not null,
    source       text          not null,
    external_id  text          not null,
    owned        boolean       not null,
    price_brl    numeric(12, 2),
    price_kind   text,
    primary key (build_id, position)
);
