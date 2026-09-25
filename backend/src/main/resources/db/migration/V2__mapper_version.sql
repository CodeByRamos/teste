-- Re-ingest the same upstream commit when our mapping rules change (e.g. new validation), so fixes
-- to how source data is interpreted reach the catalog without waiting for a new upstream revision.
alter table source_snapshot add column mapper_version text not null default 'opendb-mapper-v1';
alter table source_snapshot drop constraint source_snapshot_source_source_version_key;
alter table source_snapshot add constraint source_snapshot_version_key unique (source, source_version, mapper_version);
