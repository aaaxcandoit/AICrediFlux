package aicrediflux.token.flashsale.service;

public class NonRetryableFlashSaleException extends RuntimeException {
    public NonRetryableFlashSaleException(String message) { super(message); }
}
