-- EventPulse v7: seed YouTube topic aliases.
--
-- Key shape: (interest_id, source='youtube', query=<channelHandle>) where the
-- handle is lowercase and stripped of any leading '@'. The aggregator looks up
-- handles exactly like Reddit looks up subreddit names.
--
-- Taxonomy expansion (UserInterestService.subscribe) cascades parent -> leaf,
-- so a 'movie' tag is enough — no need to also tag every leaf genre. Sports
-- channels get tagged at whatever level matches: NBA channel -> 'nba' leaf,
-- ESPN -> 'sport' parent (since ESPN covers everything).

CREATE OR REPLACE FUNCTION pg_temp.alias_for(interest_slug TEXT, query_text TEXT)
    RETURNS TABLE (interest_id UUID, source TEXT, query TEXT)
    LANGUAGE SQL
    STABLE
AS $$
    SELECT id, 'youtube', query_text FROM interests WHERE slug = interest_slug
$$;

INSERT INTO topic_aliases (interest_id, source, query)
-- Movies
SELECT * FROM pg_temp.alias_for('movie',  'screenrant')      UNION ALL
SELECT * FROM pg_temp.alias_for('movie',  'rottentomatoes')  UNION ALL

-- Music (Vevo aggregates labels, so tag at the parent)
SELECT * FROM pg_temp.alias_for('music',  'vevo')            UNION ALL

-- Gaming
SELECT * FROM pg_temp.alias_for('gaming', 'ign')             UNION ALL
SELECT * FROM pg_temp.alias_for('gaming', 'gamespot')        UNION ALL

-- Sports
SELECT * FROM pg_temp.alias_for('nba',    'nba')             UNION ALL
SELECT * FROM pg_temp.alias_for('sport',  'espn')
ON CONFLICT (interest_id, source, query) DO NOTHING;
