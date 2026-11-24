package com.meanderatlas.atlas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DerivedRoleResolverTest {

    @Test
    void starredHotelResolvesToPreferredHotel() {
        DerivedRole role =
                DerivedRoleResolver.resolve(
                        PlaceCategory.HOTEL, List.of(ListKind.STARRED, ListKind.WANT_TO_GO));

        assertThat(role).isEqualTo(DerivedRole.PREFERRED_HOTEL);
    }

    @Test
    void starredNonHotelResolvesToMustSee() {
        DerivedRole role =
                DerivedRoleResolver.resolve(
                        PlaceCategory.SACRAL, List.of(ListKind.STARRED, ListKind.FAVORITE));

        assertThat(role).isEqualTo(DerivedRole.MUST_SEE);
    }

    @Test
    void favoriteWithoutStarredResolvesToFavorite() {
        DerivedRole role =
                DerivedRoleResolver.resolve(
                        PlaceCategory.RESTAURANT, List.of(ListKind.FAVORITE, ListKind.WANT_TO_GO));

        assertThat(role).isEqualTo(DerivedRole.FAVORITE);
    }

    @Test
    void wantToGoCustomOrReviewResolvesToBacklog() {
        assertThat(DerivedRoleResolver.resolve(PlaceCategory.UNCATEGORIZED, List.of()))
                .isEqualTo(DerivedRole.BACKLOG);
        assertThat(
                        DerivedRoleResolver.resolve(
                                PlaceCategory.MOUNTAIN,
                                List.of(ListKind.WANT_TO_GO, ListKind.CUSTOM, ListKind.REVIEW)))
                .isEqualTo(DerivedRole.BACKLOG);
    }
}
