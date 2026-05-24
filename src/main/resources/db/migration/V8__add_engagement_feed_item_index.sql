-- Index for the global "trending" subquery in the feed ranking.
--
-- The per-user source/interest weight subqueries filter on user_id first and
-- are served by idx_user_engagement_user_id_created_at (V1). The trending
-- term is the inverse: it sums weights across ALL users for a given
-- feed_item in the last 24h, so the dominant filter is feed_item_id, not
-- user_id. Without an index leading on feed_item_id, each ranked row
-- triggers a seq scan over user_engagement.
--
-- created_at is included as the second column so the 24h cutoff is satisfied
-- by an index range scan rather than a recheck.
CREATE INDEX idx_user_engagement_feed_item_id_created_at
    ON user_engagement(feed_item_id, created_at DESC);
