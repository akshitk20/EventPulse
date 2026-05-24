package com.eventpulse.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly retention job: deletes old, untouched feed_items so the database
 * doesn't grow unbounded under continuous polling.
 *
 * <p>FK behaviour matters here. {@code feed_item_interests} cascades from
 * {@code feed_items} — fine, we want those tag rows to disappear with the item.
 * {@code user_engagement} <i>also</i> cascades, which would silently wipe
 * SAVE/HIDE history if we deleted indiscriminately. So this job only deletes
 * items that <b>no user has ever engaged with</b> (no save, no hide, no click,
 * no view) — that protects "Saved" and "Hidden" views from rotting out beneath
 * the user, while still reclaiming the long tail of items that nobody touched.
 *
 * <p>Cutoff is 90 days on {@code fetched_at} (not {@code published_at} —
 * publishedAt can be old for backfilled items we just pulled). Runs once a
 * day at 03:00 UTC; the schedule is fine to drift a few hours either way.
 *
 * <p>This is the free-tier insurance policy referenced in the README: at
 * ~200 polled items/day a 90-day window keeps {@code feed_items} under
 * ~20k rows / ~30 MB indefinitely, well below Neon's 0.5 GB free cap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedItemRetentionJob {

    static final int RETENTION_DAYS = 90;

    private final JdbcTemplate jdbc;

    /**
     * Daily at 03:00 server time. Uses cron rather than fixedDelay so it runs
     * at a predictable off-peak hour rather than 24h after each boot.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purge() {
        int deleted = jdbc.update("""
            delete from feed_items
             where fetched_at < now() - make_interval(days => ?)
               and not exists (
                   select 1 from user_engagement ue
                    where ue.feed_item_id = feed_items.id
               )
            """, RETENTION_DAYS);
        log.info("Retention purge: removed {} feed_items older than {} days with no engagement",
                deleted, RETENTION_DAYS);
    }
}
