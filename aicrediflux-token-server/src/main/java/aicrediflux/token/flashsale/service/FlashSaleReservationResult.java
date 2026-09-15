package aicrediflux.token.flashsale.service;

public enum FlashSaleReservationResult {
    SUCCESS(0, "抢购资格已锁定"),
    NOT_READY(1, "活动尚未预热"),
    NOT_STARTED(2, "活动尚未开始"),
    ENDED(3, "活动已结束"),
    SOLD_OUT(4, "库存不足"),
    DUPLICATE(5, "每个活动只能购买一次"),
    DISABLED(6, "活动未启用");

    private final long code;
    private final String message;

    FlashSaleReservationResult(long code, String message) {
        this.code = code;
        this.message = message;
    }

    public long code() { return code; }
    public String message() { return message; }

    public static FlashSaleReservationResult fromCode(long code) {
        for (FlashSaleReservationResult value : values()) {
            if (value.code == code) return value;
        }
        throw new IllegalArgumentException("未知秒杀脚本返回码: " + code);
    }
}
