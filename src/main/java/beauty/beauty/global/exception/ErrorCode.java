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
    ALREADY_STYLIST(HttpStatus.CONFLICT,               "이미 미용사(STYLIST) 권한을 가지고 있습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST,          "현재 비밀번호가 일치하지 않습니다."),
    NEW_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST,      "새 비밀번호가 일치하지 않습니다."),

    // Stylist
    STYLIST_NOT_FOUND(HttpStatus.NOT_FOUND,       "미용사를 찾을 수 없습니다."),
    STYLIST_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND,    "미용사 프로필을 찾을 수 없습니다."),
    SERVICE_NOT_FOUND(HttpStatus.NOT_FOUND,       "서비스를 찾을 수 없습니다."),
    SERVICE_NOT_AUTHORIZED(HttpStatus.FORBIDDEN,       "해당 서비스를 수정할 권한이 없습니다."),

    // Reservation
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND,   "예약을 찾을 수 없습니다."),
    INVALID_RESERVATION_STATUS(HttpStatus.BAD_REQUEST, "잘못된 예약 상태입니다."),
    ALREADY_RESERVED(HttpStatus.CONFLICT,         "해당 시간에 이미 예약이 있습니다."),
    SERVICE_NOT_OWNED(HttpStatus.BAD_REQUEST,           "해당 미용사가 제공하는 서비스가 아닙니다."),
    OPERATING_HOURS_NOT_FOUND(HttpStatus.NOT_FOUND,    "해당 요일의 영업시간 정보가 설정되지 않았습니다."),
    HOLIDAY(HttpStatus.BAD_REQUEST,                    "해당 예약일은 휴무일입니다."),
    OUTSIDE_OPERATING_HOURS(HttpStatus.BAD_REQUEST,    "영업시간 외에는 예약할 수 없습니다."),
    NOT_MY_RESERVATION(HttpStatus.FORBIDDEN,           "본인의 예약만 처리할 수 있습니다."),
    FILE_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND,       "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST,"결제 금액이 일치하지 않습니다."),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST,        "결제 처리에 실패했습니다."),
    NOT_MY_PAYMENT(HttpStatus.FORBIDDEN,               "본인의 결제만 처리할 수 있습니다."),
    PAYMENT_ALREADY_PAID(HttpStatus.CONFLICT,          "이미 결제가 완료된 예약입니다."),
    PAYMENT_ALREADY_REFUNDED(HttpStatus.CONFLICT,      "이미 환불 처리된 예약입니다."),
    REFUND_NOT_ALLOWED(HttpStatus.BAD_REQUEST,         "결제 완료 상태인 건만 환불할 수 있습니다."),
    TOSS_API_FAILED(HttpStatus.BAD_GATEWAY,            "결제 처리 중 외부 오류가 발생했습니다."),

    // Coupon
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND,             "존재하지 않는 쿠폰입니다."),
    COUPON_NOT_OWNED(HttpStatus.FORBIDDEN,             "해당 쿠폰의 소유 미용사만 발급할 수 있습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT,         "이미 발급된 쿠폰입니다."),
    COUPON_INVALID(HttpStatus.BAD_REQUEST,             "유효하지 않거나 이미 사용된 쿠폰입니다."),
    COUPON_MIN_PRICE_NOT_MET(HttpStatus.BAD_REQUEST,   "최소 결제 금액이 충족되지 않았습니다."),

    // Chat
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND,     "채팅방을 찾을 수 없습니다."),
    CHAT_ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN,      "채팅방에 접근 권한이 없습니다."),

    // Review
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT,    "이미 리뷰를 작성했습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND,        "리뷰를 찾을 수 없습니다."),

    // Hair Analysis
    FACE_NOT_DETECTED(HttpStatus.BAD_REQUEST,     "얼굴을 인식할 수 없습니다. 얼굴이 잘 보이는 사진을 올려주세요."),
    HAIR_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 분석에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // Community
    POST_NOT_FOUND(HttpStatus.NOT_FOUND,           "게시글을 찾을 수 없습니다."),
    NOT_MY_POST(HttpStatus.FORBIDDEN,              "본인의 게시글만 수정/삭제할 수 있습니다."),
    DISTRICT_NOT_VERIFIED(HttpStatus.BAD_REQUEST,  "위치 인증이 필요합니다."),
    RESERVATION_NOT_MINE(HttpStatus.FORBIDDEN,     "본인의 예약만 연결할 수 있습니다."),
    LOCATION_DETECTION_FAILED(HttpStatus.BAD_REQUEST, "위치 감지에 실패했습니다. 다시 시도해 주세요."),

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
