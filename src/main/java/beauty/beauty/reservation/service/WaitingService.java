package beauty.beauty.reservation.service;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.reservation.entity.Waiting;
import beauty.beauty.reservation.repository.WaitingRepository;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class WaitingService {

    private final WaitingRepository waitingRepository;
    private final UserRepository userRepository;
    private final StylistProfileRepository stylistProfileRepository;

    @Transactional
    public void registerWaiting(Long userId, Long stylistProfileId, LocalDate date, LocalTime time) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        StylistProfile stylistProfile = stylistProfileRepository.findById(stylistProfileId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_NOT_FOUND));

        Waiting waiting = Waiting.builder()
                .user(user)
                .stylistProfile(stylistProfile)
                .waitingDate(date)
                .waitingTime(time)
                .build();
        
        waitingRepository.save(waiting);
    }
}
