package beauty.beauty.favorite.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FavoriteToggleResponse {
    private boolean favorited;
    private int favoriteCount;
}
