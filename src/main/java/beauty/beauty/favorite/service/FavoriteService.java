package beauty.beauty.favorite.service;

import beauty.beauty.favorite.dto.FavoriteToggleResponse;
import beauty.beauty.stylist.dto.StylistProfileResponse;
import java.util.List;

public interface FavoriteService {
    List<StylistProfileResponse> getMyFavorites(Long userId);
    FavoriteToggleResponse toggleFavorite(Long userId, Long stylistProfileId);
    boolean checkStatus(Long userId, Long stylistProfileId);
}
