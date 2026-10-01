-- What the store called the product and how it was matched, so operators can audit the bot's matches.
alter table crawl_page add column listing_title text;
alter table crawl_page add column match_method text;
