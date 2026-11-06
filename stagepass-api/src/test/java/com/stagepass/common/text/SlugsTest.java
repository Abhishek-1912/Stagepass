package com.stagepass.common.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void slugifiesUnicodeAndPunctuation() {
        assertThat(Slugs.slugify("Sunburn Goa 2026 — Day 1!")).isEqualTo("sunburn-goa-2026-day-1");
        assertThat(Slugs.slugify("Café Délice")).isEqualTo("cafe-delice");
    }

    @Test
    void emptySlugFallsBack() {
        assertThat(Slugs.slugify("!!!")).isEqualTo("event");
        assertThat(Slugs.slugify(null)).isEqualTo("event");
    }

    @Test
    void longTitlesAreTruncatedWithoutTrailingDash() {
        String slug = Slugs.slugify("a".repeat(59) + " bcd");
        assertThat(slug).hasSizeLessThanOrEqualTo(60).doesNotEndWith("-");
    }

    @Test
    void uniqueAppendsSuffix() {
        assertThat(Slugs.unique("gig")).matches("^gig-[a-z0-9]{6}$");
        assertThat(Slugs.unique("gig")).isNotEqualTo(Slugs.unique("gig"));
    }
}
