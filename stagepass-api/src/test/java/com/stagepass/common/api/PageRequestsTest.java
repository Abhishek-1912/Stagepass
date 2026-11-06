package com.stagepass.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestsTest {

    @Test
    void clampsPageSize() {
        assertThat(PageRequests.of(null, 500, Sort.unsorted()).getPageSize()).isEqualTo(100);
        assertThat(PageRequests.of(null, 0, Sort.unsorted()).getPageSize()).isEqualTo(1);
        assertThat(PageRequests.of(null, null, Sort.unsorted()).getPageSize()).isEqualTo(20);
        assertThat(PageRequests.of(-3, null, Sort.unsorted()).getPageNumber()).isZero();
    }

    @Test
    void mapsPageToResponse() {
        Pageable pageable = PageRequests.of(1, 2, Sort.unsorted());
        PageResponse<String> response = PageResponse.of(new PageImpl<>(List.of(3, 4), pageable, 5), i -> "#" + i);

        assertThat(response.items()).containsExactly("#3", "#4");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
