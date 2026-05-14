package beauty.beauty.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // Auth
    DUPLICATE_USERNAME(HttpStatus.CONFLICT,       "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT,          "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,  "아이디 또는 비밀번호가 잘못되었습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN,      "이메일 인증이 완료되지 않았습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,        "인증 토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED,        "유효하지 않은 토큰입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND,          "사용자를 찾을 수 없습니다."),

    // Stylist
    STYLIST_NOT_FOUND(HttpStatus.NOT_FOUND,       "미용사를 찾을 수 없습니다."),
    SERVICE_NOT_FOUND(HttpStatus.NOT_FOUND,       "서비스를 찾을 수 없습니다."),

    // Reservation
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND,   "예약을 찾을 수 없습니다."),
    INVALID_RESERVATION_STATUS(HttpStatus.BAD_REQUEST, "잘못된 예약 상태입니다."),
    ALREADY_RESERVED(HttpStatus.CONFLICT,         "해당 시간에 이미 예약이 있습니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND,       "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST,"결제 금액이 일치하지 않습니다."),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST,        "결제 처리에 실패했습니다."),

    // Chat
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND,     "채팅방을 찾을 수 없습니다."),

    // Review
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT,    "이미 리뷰를 작성했습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND,        "리뷰를 찾을 수 없습니다."),

    // Hair Analysis
    FACE_NOT_DETECTED(HttpStatus.BAD_REQUEST,     "얼굴을 인식할 수 없습니다. 얼굴이 잘 보이는 사진을 올려주세요."),
    HAIR_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 분석에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // Common
    FORBIDDEN(HttpStatus.FORBIDDEN,               "접근 권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
