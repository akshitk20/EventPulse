-- Postgres full-text search for feed_items.title.
--
-- Why a generated column (vs. trigger): the title is the only input and never
-- changes meaning by row. A STORED generated column gives us indexing-on-write
-- with zero application-side wiring — every aggregator (HN, Reddit, sportsdb)
-- already inserts via FeedService.upsertBySource and Postgres recomputes the
-- vector automatically.
--
-- Why 'english': all three sources are predominantly English. Stop-words and
-- stemming behave correctly for the substring matches the LIKE-based search
-- could never handle ("rusty" -> "rust", "running" -> "run").
--
-- coalesce(title, '') is defensive — title is currently NOT NULL, but the
-- generated-column expression must be IMMUTABLE on every row, and a future
-- relaxation of NOT NULL would otherwise leave rows un-indexable.

ALTER TABLE feed_items
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('english', coalesce(title, ''))) STORED;

CREATE INDEX idx_feed_items_search_vector ON feed_items USING GIN (search_vector);
