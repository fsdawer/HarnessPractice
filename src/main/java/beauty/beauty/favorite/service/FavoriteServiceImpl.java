package beauty.beauty.favorite.service;

import beauty.beauty.favorite.entity.FavoriteStylist;
import beauty.beauty.favorite.repository.FavoriteStylistRepository;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.stylist.dto.StylistProfileResponse;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteStylistRepository favoriteStylistRepository;
    private final StylistProfileRepository stylistProfileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StylistProfileResponse> getMyFavorites(Long userId) {
        return favoriteStylistRepository.findByUserIdWithDetails(userId)
                .stream()
                .map(fs -> StylistProfileResponse.from(fs.getStylistProfile()))
                .toList();
    }

    @Override
    @Transactional
    public boolean toggleFavorite(Long userId, Long stylistProfileId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        StylistProfile stylistProfile = stylistProfileRepository.findById(stylistProfileId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_NOT_FOUND));

        var existing = favoriteStylistRepository.findByUserIdAndStylistProfileId(userId, stylistProfileId);

        if (existing.isPresent()) {
            favoriteStylistRepository.delete(existing.get());
            stylistProfileRepository.decrementFavoriteCount(stylistProfileId);
            return false;
        } else {
            FavoriteStylist favoriteStylist = FavoriteStylist.builder()
                    .user(user)
                    .stylistProfile(stylistProfile)
                    .build();
            favoriteStylistRepository.save(favoriteStylist);
            stylistProfileRepository.incrementFavoriteCount(stylistProfileId);
            return true;
        }
    }
}
