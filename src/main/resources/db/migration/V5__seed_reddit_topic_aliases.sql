-- EventPulse v5: seed Reddit topic aliases.
--
-- Aliases here are matched by exact lowercase equality against a Reddit post's
-- subreddit name (lowercased by the aggregator). Keep entries lowercase.
--
-- The taxonomy's descendant-expansion (UserInterestService.subscribe) means a
-- user subscribed to "Movies" automatically gets the leaf interests, so it's
-- enough to tag r/MarvelStudios posts with movie-mcu — they'll surface for
-- anyone subscribed to Movies (parent) or MCU (leaf).

CREATE OR REPLACE FUNCTION pg_temp.alias_for(interest_slug TEXT, query_text TEXT)
    RETURNS TABLE (interest_id UUID, source TEXT, query TEXT)
    LANGUAGE SQL
    STABLE
AS $$
    SELECT id, 'reddit', query_text FROM interests WHERE slug = interest_slug
$$;

INSERT INTO topic_aliases (interest_id, source, query)
-- Movies
SELECT * FROM pg_temp.alias_for('movie',          'movies')                UNION ALL
SELECT * FROM pg_temp.alias_for('movie',          'moviedetails')          UNION ALL
SELECT * FROM pg_temp.alias_for('movie-mcu',      'marvelstudios')         UNION ALL

-- TV / Anime
SELECT * FROM pg_temp.alias_for('tv',             'television')            UNION ALL
SELECT * FROM pg_temp.alias_for('tv-anime',       'anime')                 UNION ALL

-- Music
SELECT * FROM pg_temp.alias_for('music',          'music')                 UNION ALL
SELECT * FROM pg_temp.alias_for('music-pop',      'popheads')              UNION ALL
SELECT * FROM pg_temp.alias_for('music-hip-hop',  'hiphopheads')           UNION ALL
SELECT * FROM pg_temp.alias_for('music-bollywood','bollyblindsngossip')    UNION ALL

-- Gaming
SELECT * FROM pg_temp.alias_for('gaming',         'gaming')                UNION ALL
SELECT * FROM pg_temp.alias_for('gaming',         'games')                 UNION ALL
SELECT * FROM pg_temp.alias_for('gaming-pc',      'pcgaming')
ON CONFLICT (interest_id, source, query) DO NOTHING;
