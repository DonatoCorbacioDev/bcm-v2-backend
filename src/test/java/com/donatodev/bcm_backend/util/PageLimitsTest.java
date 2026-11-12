package com.donatodev.bcm_backend.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PageLimitsTest {

    @Test
    @DisplayName("a page within the limit is built as asked")
    void withinTheLimit() {
        assertThat(PageLimits.of(2, PageLimits.MAX_PAGE_SIZE, Sort.unsorted()).getPageSize()).isEqualTo(PageLimits.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("one row more than the limit, an empty or a negative page is refused, not clamped")
    void outOfRangeIsRefused() {
        assertThatThrownBy(() -> PageLimits.of(0, PageLimits.MAX_PAGE_SIZE + 1, Sort.unsorted()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageLimits.of(0, 0, Sort.unsorted())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageLimits.of(-1, 10, Sort.unsorted())).isInstanceOf(IllegalArgumentException.class);
    }
}
