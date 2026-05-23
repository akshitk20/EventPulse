-- EventPulse v3: seed HN topic aliases.
--
-- Each row maps an interest (by slug) to a keyword the HN aggregator searches
-- for in story titles. Whole-word, case-insensitive matching is done in code,
-- so keep entries in lowercase.
--
-- Tilted toward what HN's audience actually posts about: tech, gaming, movies,
-- a few music/TV brand names. Cricket and football show up rarely, so we only
-- seed broad terms there. We can expand later via the same migration pattern.

CREATE OR REPLACE FUNCTION pg_temp.alias_for(interest_slug TEXT, query_text TEXT)
    RETURNS TABLE (interest_id UUID, source TEXT, query TEXT)
    LANGUAGE SQL
    STABLE
AS $$
    SELECT id, 'hn', query_text FROM interests WHERE slug = interest_slug
$$;

INSERT INTO topic_aliases (interest_id, source, query)
SELECT * FROM pg_temp.alias_for('gaming',          'gaming')           UNION ALL
SELECT * FROM pg_temp.alias_for('gaming',          'video game')       UNION ALL
SELECT * FROM pg_temp.alias_for('gaming-pc',       'pc gaming')        UNION ALL
SELECT * FROM pg_temp.alias_for('gaming-pc',       'steam')            UNION ALL
SELECT * FROM pg_temp.alias_for('gaming-console',  'playstation')      UNION ALL
SELECT * FROM pg_temp.alias_for('gaming-console',  'xbox')             UNION ALL
SELECT * FROM pg_temp.alias_for('gaming-console',  'nintendo')         UNION ALL
SELECT * FROM pg_temp.alias_for('gaming-indie',    'indie game')       UNION ALL
SELECT * FROM pg_temp.alias_for('gaming-esports',  'esports')          UNION ALL

SELECT * FROM pg_temp.alias_for('movie',           'movie')            UNION ALL
SELECT * FROM pg_temp.alias_for('movie',           'film')             UNION ALL
SELECT * FROM pg_temp.alias_for('movie-scifi',     'sci-fi')           UNION ALL
SELECT * FROM pg_temp.alias_for('movie-scifi',     'science fiction')  UNION ALL
SELECT * FROM pg_temp.alias_for('movie-mcu',       'marvel')           UNION ALL
SELECT * FROM pg_temp.alias_for('movie-mcu',       'mcu')              UNION ALL

SELECT * FROM pg_temp.alias_for('tv',              'tv show')          UNION ALL
SELECT * FROM pg_temp.alias_for('tv',              'streaming')        UNION ALL
SELECT * FROM pg_temp.alias_for('tv',              'netflix')          UNION ALL
SELECT * FROM pg_temp.alias_for('tv',              'hbo')              UNION ALL
SELECT * FROM pg_temp.alias_for('tv-anime',        'anime')            UNION ALL

SELECT * FROM pg_temp.alias_for('music',           'music')            UNION ALL
SELECT * FROM pg_temp.alias_for('music',           'spotify')          UNION ALL
SELECT * FROM pg_temp.alias_for('music-electronic','electronic music') UNION ALL

SELECT * FROM pg_temp.alias_for('f1',              'formula 1')        UNION ALL
SELECT * FROM pg_temp.alias_for('f1',              'formula one')      UNION ALL
SELECT * FROM pg_temp.alias_for('f1',              'f1')               UNION ALL
SELECT * FROM pg_temp.alias_for('football',        'world cup')        UNION ALL
SELECT * FROM pg_temp.alias_for('cricket',         'cricket')
ON CONFLICT (interest_id, source, query) DO NOTHING;
