package beauty.beauty.favorite.service;

import beauty.beauty.stylist.dto.StylistProfileResponse;
import java.util.List;

public interface FavoriteService {
    List<StylistProfileResponse> getMyFavorites(Long userId);
    boolean toggleFavorite(Long userId, Long stylistProfileId);
}
