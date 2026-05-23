package com.eventpulse.aggregator.sportsdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Subset of TheSportsDB event JSON we care about. The free API returns dozens
 * of fields per event (lineup, video, weather, season-specific stats); we keep
 * only what the feed needs.
 *
 * Quirks worth knowing:
 *   - Numeric scores arrive as Strings (or JSON null), not numbers.
 *   - When a league has no events, the wrapper returns {"events": null}.
 *   - strTimestamp is the most reliable time field — UTC ISO-8601. dateEvent +
 *     strTime are venue-local and may drift on TBD fixtures, so we ignore them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SportsDbEvent(
    String idEvent,
    String strEvent,
    String strSport,
    String idLeague,
    String strLeague,
    String strHomeTeam,
    String strAwayTeam,
    String intHomeScore,
    String intAwayScore,
    String strStatus,
    String strPostponed,
    String strTimestamp,
    String dateEvent,
    String strTime,
    String strThumb,
    String strPoster,
    String strLeagueBadge
) {
    public boolean isPresentable() {
        return idEvent != null
            && strEvent != null && !strEvent.isBlank()
            && !"yes".equalsIgnoreCase(strPostponed);
    }

    public String permalinkUrl() {
        return "https://www.thesportsdb.com/event/" + idEvent;
    }

    /**
     * Prefer the canonical event title; fall back to "Home vs Away" if both teams
     * are present but strEvent is somehow blank (rare but observed on TBD fixtures).
     */
    public String displayTitle() {
        if (strEvent != null && !strEvent.isBlank()) return strEvent;
        if (strHomeTeam != null && !strHomeTeam.isBlank()
            && strAwayTeam != null && !strAwayTeam.isBlank()) {
            return strHomeTeam + " vs " + strAwayTeam;
        }
        return strEvent;
    }

    public OffsetDateTime startsAt() {
        if (strTimestamp == null || strTimestamp.isBlank()) return null;
        try {
            return OffsetDateTime.parse(strTimestamp);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public String thumbnailUrl() {
        if (strThumb != null && !strThumb.isBlank()) return strThumb;
        if (strPoster != null && !strPoster.isBlank()) return strPoster;
        if (strLeagueBadge != null && !strLeagueBadge.isBlank()) return strLeagueBadge;
        return null;
    }
}
