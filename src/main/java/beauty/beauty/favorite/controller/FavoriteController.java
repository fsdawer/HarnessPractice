package beauty.beauty.favorite.controller;

import beauty.beauty.favorite.dto.FavoriteToggleResponse;
import beauty.beauty.favorite.service.FavoriteService;
import beauty.beauty.global.annotation.LoginUserId;
import beauty.beauty.stylist.dto.StylistProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping("/stylists")
    public ResponseEntity<List<StylistProfileResponse>> getMyFavorites(@LoginUserId Long userId) {
        return ResponseEntity.ok(favoriteService.getMyFavorites(userId));
    }

    @PostMapping("/stylists/{stylistProfileId}")
    public ResponseEntity<FavoriteToggleResponse> toggleFavorite(
            @LoginUserId Long userId,
            @PathVariable Long stylistProfileId) {
        return ResponseEntity.ok(favoriteService.toggleFavorite(userId, stylistProfileId));
    }

    @GetMapping("/stylists/{stylistProfileId}/status")
    public ResponseEntity<Map<String, Boolean>> checkStatus(
            @LoginUserId Long userId,
            @PathVariable Long stylistProfileId) {
        return ResponseEntity.ok(Map.of("favorited", favoriteService.checkStatus(userId, stylistProfileId)));
    }
}
