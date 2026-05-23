-- EventPulse v4: seed TheSportsDB topic aliases.
--
-- Aliases here are matched by exact lowercase equality against either the short
-- league key the aggregator passes ("epl", "f1", "ipl", "nba"), the lowercased
-- strLeague the API returns, or the lowercased strSport. Keep entries lowercase.
--
-- The interests subscribed-to-Sports user already gets the leaf interests
-- (premier-league, f1, ipl, nba) via descendant expansion at subscribe time, so
-- we don't also tag every event with the "sport" interest — leaf tagging is
-- enough to surface items in the feed.

CREATE OR REPLACE FUNCTION pg_temp.alias_for(interest_slug TEXT, query_text TEXT)
    RETURNS TABLE (interest_id UUID, source TEXT, query TEXT)
    LANGUAGE SQL
    STABLE
AS $$
    SELECT id, 'sportsdb', query_text FROM interests WHERE slug = interest_slug
$$;

INSERT INTO topic_aliases (interest_id, source, query)
-- Football / Premier League
SELECT * FROM pg_temp.alias_for('premier-league', 'epl')                       UNION ALL
SELECT * FROM pg_temp.alias_for('premier-league', 'english premier league')   UNION ALL
SELECT * FROM pg_temp.alias_for('premier-league', 'premier league')           UNION ALL
SELECT * FROM pg_temp.alias_for('football',       'soccer')                    UNION ALL
SELECT * FROM pg_temp.alias_for('football',       'english premier league')   UNION ALL
SELECT * FROM pg_temp.alias_for('football',       'epl')                       UNION ALL

-- Formula 1
SELECT * FROM pg_temp.alias_for('f1',             'f1')                        UNION ALL
SELECT * FROM pg_temp.alias_for('f1',             'formula 1')                 UNION ALL
SELECT * FROM pg_temp.alias_for('f1',             'motorsport')                UNION ALL

-- Cricket / IPL
SELECT * FROM pg_temp.alias_for('ipl',            'ipl')                       UNION ALL
SELECT * FROM pg_temp.alias_for('ipl',            'indian premier league')     UNION ALL
SELECT * FROM pg_temp.alias_for('cricket',        'cricket')                   UNION ALL
SELECT * FROM pg_temp.alias_for('cricket',        'ipl')                       UNION ALL
SELECT * FROM pg_temp.alias_for('cricket',        'indian premier league')     UNION ALL

-- Basketball / NBA
SELECT * FROM pg_temp.alias_for('nba',            'nba')                       UNION ALL
SELECT * FROM pg_temp.alias_for('nba',            'national basketball association') UNION ALL
SELECT * FROM pg_temp.alias_for('basketball',     'basketball')                UNION ALL
SELECT * FROM pg_temp.alias_for('basketball',     'nba')
ON CONFLICT (interest_id, source, query) DO NOTHING;
