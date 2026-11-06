package com.stagepass.discovery.repository;

import com.stagepass.discovery.api.EventSearchParams;
import com.stagepass.discovery.api.PublicEventCard;
import com.stagepass.discovery.service.DateRanges.Range;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only SQL for public discovery. Every filter is a bound parameter; the only dynamic SQL is the choice of
 * fixed clauses. Public = PUBLISHED and not yet ended.
 */
@Repository
public class PublicEventQueries {

    private static final String DISTANCE = """
            6371 * 2 * asin(sqrt(power(sin(radians(v.lat - :lat) / 2), 2)
              + cos(radians(:lat)) * cos(radians(v.lat)) * power(sin(radians(v.lng - :lng) / 2), 2)))""";

    private static final RowMapper<PublicEventCard> CARD = (rs, i) -> new PublicEventCard(
            rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"), rs.getString("summary"),
            rs.getString("cover_image_url"), rs.getObject("starts_at", OffsetDateTime.class),
            rs.getObject("ends_at", OffsetDateTime.class), rs.getBoolean("is_online"), rs.getString("venue_name"),
            rs.getString("city"), rs.getString("category_slug"), rs.getString("category_name"),
            (Long) rs.getObject("min_price"), rs.getLong("available") <= 0, rs.getBoolean("is_featured"),
            (Double) rs.getObject("distance_km"));

    private final NamedParameterJdbcTemplate jdbc;

    public PublicEventQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<PublicEventCard> search(EventSearchParams p, Range range, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String inner = baseSelect(p, range, params);
        StringBuilder outer = new StringBuilder("select * from (").append(inner).append(") s where true");
        if (p.sort() == EventSearchParams.Sort.NEARBY && p.hasLocation()) {
            outer.append(" and distance_km <= :radius");
            params.addValue("radius", p.radiusKm());
        }
        Long total = jdbc.queryForObject("select count(*) from (" + outer + ") c", params, Long.class);
        outer.append(" order by ").append(orderBy(p)).append(" limit :limit offset :offset");
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<PublicEventCard> rows = jdbc.query(outer.toString(), params, CARD);
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    /** Distinct venue cities with at least one listed event, most events first. */
    public List<CityCount> cities() {
        return jdbc.query("""
                select min(v.city) as city, count(*) as n
                from events e join venues v on v.id = e.venue_id
                where e.status = 'PUBLISHED' and e.ends_at > now()
                group by lower(v.city) order by n desc, min(v.city) asc limit 50
                """, new MapSqlParameterSource(), (rs, i) -> new CityCount(rs.getString("city"), rs.getInt("n")));
    }

    public record CityCount(String city, int events) {
    }

    private static String baseSelect(EventSearchParams p, Range range, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder("""
                select e.id, e.slug, e.title, e.summary, e.cover_image_url, e.starts_at, e.ends_at, e.is_online,
                       e.is_featured, e.popularity_score, v.name as venue_name, v.city,
                       c.slug as category_slug, c.name as category_name, t.min_price, t.available,
                """);
        if (p.hasLocation()) {
            sql.append(" case when v.lat is null or e.is_online then null else ").append(DISTANCE)
                    .append(" end as distance_km");
            params.addValue("lat", p.lat()).addValue("lng", p.lng());
        } else {
            sql.append(" cast(null as double precision) as distance_km");
        }
        sql.append("""

                from events e
                join event_categories c on c.id = e.category_id
                left join venues v on v.id = e.venue_id
                left join lateral (
                    select min(tt.price_paise) filter (where tt.status = 'ACTIVE') as min_price,
                           coalesce(sum(greatest(ti.total - ti.sold - ti.held, 0))
                                    filter (where tt.status = 'ACTIVE'), 0) as available
                    from ticket_types tt join ticket_inventory ti on ti.ticket_type_id = tt.id
                    where tt.event_id = e.id
                ) t on true
                where e.status = 'PUBLISHED' and e.ends_at > now()
                """);
        if (p.q() != null) {
            sql.append("""
                     and (e.search_vector @@ websearch_to_tsquery('simple', :q)
                          or word_similarity(:q, e.title) > 0.45
                          or lower(v.name) like :qLike escape '\\'
                          or lower(v.city) like :qLike escape '\\')
                    """);
            params.addValue("q", p.q()).addValue("qLike", "%" + escapeLike(p.q().toLowerCase(Locale.ROOT)) + "%");
        }
        if (p.city() != null) {
            sql.append(p.includeOnlineWithCity() ? " and (lower(v.city) = lower(:city) or e.is_online)"
                    : " and lower(v.city) = lower(:city)");
            params.addValue("city", p.city());
        }
        if (p.category() != null) {
            sql.append(" and c.slug = :category");
            params.addValue("category", p.category());
        }
        if (range != null) {
            sql.append(" and e.starts_at < :rangeEnd and e.ends_at > :rangeStart");
            params.addValue("rangeStart", range.start()).addValue("rangeEnd", range.end());
        }
        if (p.startsWithinDays() != null) {
            sql.append(" and e.starts_at < now() + make_interval(days => :withinDays)");
            params.addValue("withinDays", p.startsWithinDays());
        }
        if (p.minPricePaise() != null) {
            sql.append(" and t.min_price >= :minPrice");
            params.addValue("minPrice", p.minPricePaise());
        }
        if (p.maxPricePaise() != null) {
            sql.append(" and t.min_price <= :maxPrice");
            params.addValue("maxPrice", p.maxPricePaise());
        }
        if (p.freeOnly()) {
            sql.append(" and t.min_price = 0");
        }
        if (p.featuredOnly()) {
            sql.append(" and e.is_featured");
        }
        if (p.excludeOnline()) {
            sql.append(" and not e.is_online");
        }
        if (p.organizerId() != null) {
            sql.append(" and e.organizer_id = :organizerId");
            params.addValue("organizerId", p.organizerId());
        }
        if (p.hasLocation() && p.sort() == EventSearchParams.Sort.NEARBY) {
            // Cheap bounding box before the exact haversine distance.
            double latDelta = p.radiusKm() / 111.0;
            double lngDelta = p.radiusKm() / (111.0 * Math.max(0.1, Math.cos(Math.toRadians(p.lat()))));
            sql.append(" and not e.is_online and v.lat between :minLat and :maxLat and v.lng between :minLng and :maxLng");
            params.addValue("minLat", p.lat() - latDelta).addValue("maxLat", p.lat() + latDelta)
                    .addValue("minLng", p.lng() - lngDelta).addValue("maxLng", p.lng() + lngDelta);
        }
        return sql.toString();
    }

    private static String orderBy(EventSearchParams p) {
        return switch (p.sort() == null ? EventSearchParams.Sort.POPULAR : p.sort()) {
            case SOONEST -> "starts_at asc, id";
            case NEARBY -> "distance_km asc nulls last, starts_at asc, id";
            case POPULAR -> "is_featured desc, popularity_score desc, starts_at asc, id";
        };
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
