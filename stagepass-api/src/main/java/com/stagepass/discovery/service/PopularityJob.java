package com.stagepass.discovery.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hourly "Popular" ranking: tickets confirmed in the last 7 days count fully, 8-30 days ago half, and each save
 * made in the last 30 days counts as half a ticket (spec 8.7).
 * Only rows whose score changes are written.
 */
@Component
public class PopularityJob {

    private final JdbcTemplate jdbc;

    public PopularityJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 5 * * * *")
    void scheduled() {
        recompute();
    }

    /** Returns how many events got a new score. */
    @Transactional
    public int recompute() {
        return jdbc.update("""
                with scores as (
                    select e.id, coalesce(sum(case when b.confirmed_at >= now() - interval '7 days' then i.quantity
                                                   else 0.5 * i.quantity end), 0)::double precision as score
                    from events e
                    left join bookings b on b.event_id = e.id
                         and b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED')
                         and b.confirmed_at >= now() - interval '30 days'
                    left join booking_items i on i.booking_id = b.id
                    where e.status = 'PUBLISHED'
                    group by e.id
                ),
                saves as (
                    select s.event_id, 0.5 * count(*) as score from saved_events s
                    where s.created_at >= now() - interval '30 days' group by s.event_id
                ),
                totals as (
                    select sc.id, sc.score + coalesce(sv.score, 0)::double precision as score
                    from scores sc left join saves sv on sv.event_id = sc.id
                )
                update events e set popularity_score = s.score
                from totals s
                where e.id = s.id and e.popularity_score is distinct from s.score
                """);
    }
}
