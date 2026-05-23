-- EventPulse v2: seed the interest taxonomy.
--
-- We pin UUIDs deterministically using uuid_generate_v5 over a project namespace,
-- so child rows can reference parents by recomputing the same UUID, and so re-runs
-- against a partially-seeded DB are idempotent.
--
-- Approach: a CTE per top-level category with explicit slugs, then INSERT ... ON
-- CONFLICT DO NOTHING. This keeps the seed readable and avoids depending on
-- uuid-ossp; we use md5-based UUIDs since pgcrypto is already enabled.

-- Helper: stable UUID derived from a slug, so parent->child wiring is purely by slug.
CREATE OR REPLACE FUNCTION pg_temp.interest_uuid(slug TEXT)
    RETURNS UUID
    LANGUAGE SQL
    IMMUTABLE
AS $$
    SELECT encode(digest('eventpulse-interest:' || slug, 'md5'), 'hex')::uuid
$$;

-- Top-level interests
INSERT INTO interests (id, slug, display_name, category, parent_id) VALUES
    (pg_temp.interest_uuid('sport'),  'sport',  'Sports',    'SPORT',  NULL),
    (pg_temp.interest_uuid('movie'),  'movie',  'Movies',    'MOVIE',  NULL),
    (pg_temp.interest_uuid('tv'),     'tv',     'TV Shows',  'TV',     NULL),
    (pg_temp.interest_uuid('music'),  'music',  'Music',     'MUSIC',  NULL),
    (pg_temp.interest_uuid('gaming'), 'gaming', 'Gaming',    'GAMING', NULL)
ON CONFLICT (slug) DO NOTHING;

-- Sports children
INSERT INTO interests (id, slug, display_name, category, parent_id) VALUES
    (pg_temp.interest_uuid('cricket'),    'cricket',    'Cricket',         'SPORT', pg_temp.interest_uuid('sport')),
    (pg_temp.interest_uuid('football'),   'football',   'Football/Soccer', 'SPORT', pg_temp.interest_uuid('sport')),
    (pg_temp.interest_uuid('basketball'), 'basketball', 'Basketball',      'SPORT', pg_temp.interest_uuid('sport')),
    (pg_temp.interest_uuid('tennis'),     'tennis',     'Tennis',          'SPORT', pg_temp.interest_uuid('sport')),
    (pg_temp.interest_uuid('f1'),         'f1',         'Formula 1',       'SPORT', pg_temp.interest_uuid('sport'))
ON CONFLICT (slug) DO NOTHING;

-- Cricket -> leagues
INSERT INTO interests (id, slug, display_name, category, parent_id) VALUES
    (pg_temp.interest_uuid('ipl'),    'ipl',    'IPL',                'SPORT', pg_temp.interest_uuid('cricket')),
    (pg_temp.interest_uuid('t20wc'),  't20wc',  'ICC T20 World Cup',  'SPORT', pg_temp.interest_uuid('cricket')),
    (pg_temp.interest_uuid('odiwc'),  'odiwc',  'ICC ODI World Cup',  'SPORT', pg_temp.interest_uuid('cricket'))
ON CONFLICT (slug) DO NOTHING;

-- Football -> leagues
INSERT INTO interests (id, slug, display_name, category, parent_id) VALUES
    (pg_temp.interest_uuid('premier-league'), 'premier-league', 'Premier League', 'SPORT', pg_temp.interest_uuid('football')),
    (pg_temp.interest_uuid('la-liga'),        'la-liga',        'La Liga',        'SPORT', pg_temp.interest_uuid('football')),
    (pg_temp.interest_uuid('champions-league'), 'champions-league', 'UEFA Champions League', 'SPORT', pg_temp.interest_uuid('football')),
    (pg_temp.interest_uuid('fifa-wc'),        'fifa-wc',        'FIFA World Cup', 'SPORT', pg_temp.interest_uuid('football'))
ON CONFLICT (slug) DO NOTHING;

-- Basketball -> leagues
INSERT INTO interests (id, slug, display_name, category, parent_id) VALUES
    (pg_temp.interest_uuid('nba'), 'nba', 'NBA', 'SPORT', pg_temp.interest_uuid('basketball'))
ON CONFLICT (slug) DO NOTHING;

-- Movies children (broad genres)
INSERT INTO interests (id, slug, display_name, category, parent_id) VALUES
    (pg_temp.interest_uuid('movie-action'),  'movie-action',  'Action Movies',          'MOVIE', pg_temp.interest_uuid('movie')),
    (pg_temp.interest_uuid('movie-scifi'),   'movie-scifi',   'Sci-Fi Movies',          'MOVIE', pg_temp.interest_uuid('movie')),
    (pg_temp.interest_uuid('movie-drama'),   'movie-drama',   'Drama Movies',           'MOVIE', pg_temp.interest_uuid('movie')),
    (pg_temp.interest_uuid('movie-mcu'),     'movie-mcu',     'Marvel Cinematic Universe','MOVIE', pg_temp.interest_uuid('movie'))
ON CONFLICT (slug) DO NOTHING;

-- TV children
INSERT INTO interests (id, slug, display_name, category, parent_id) VALUES
    (pg_temp.interest_uuid('tv-drama'),     'tv-drama',     'TV Drama',     'TV', pg_temp.interest_uuid('tv')),
    (pg_temp.interest_uuid('tv-comedy'),    'tv-comedy',    'TV Comedy',    'TV', pg_temp.interest_uuid('tv')),
    (pg_temp.interest_uuid('tv-anime'),     'tv-anime',     'Anime',        'TV', pg_temp.interest_uuid('tv')),
    (pg_temp.interest_uuid('tv-reality'),   'tv-reality',   'Reality TV',   'TV', pg_temp.interest_uuid('tv'))
ON CONFLICT (slug) DO NOTHING;

-- Music children
INSERT INTO interests (id, slug, display_name, category, parent_id) VALUES
    (pg_temp.interest_uuid('music-pop'),       'music-pop',       'Pop',         'MUSIC', pg_temp.interest_uuid('music')),
    (pg_temp.interest_uuid('music-rock'),      'music-rock',      'Rock',        'MUSIC', pg_temp.interest_uuid('music')),
    (pg_temp.interest_uuid('music-hip-hop'),   'music-hip-hop',   'Hip-Hop',     'MUSIC', pg_temp.interest_uuid('music')),
    (pg_temp.interest_uuid('music-electronic'),'music-electronic','Electronic',  'MUSIC', pg_temp.interest_uuid('music')),
    (pg_temp.interest_uuid('music-bollywood'), 'music-bollywood', 'Bollywood',   'MUSIC', pg_temp.interest_uuid('music'))
ON CONFLICT (slug) DO NOTHING;

-- Gaming children
INSERT INTO interests (id, slug, display_name, category, parent_id) VALUES
    (pg_temp.interest_uuid('gaming-esports'), 'gaming-esports', 'Esports',         'GAMING', pg_temp.interest_uuid('gaming')),
    (pg_temp.interest_uuid('gaming-pc'),      'gaming-pc',      'PC Gaming',       'GAMING', pg_temp.interest_uuid('gaming')),
    (pg_temp.interest_uuid('gaming-console'), 'gaming-console', 'Console Gaming',  'GAMING', pg_temp.interest_uuid('gaming')),
    (pg_temp.interest_uuid('gaming-indie'),   'gaming-indie',   'Indie Games',     'GAMING', pg_temp.interest_uuid('gaming'))
ON CONFLICT (slug) DO NOTHING;
