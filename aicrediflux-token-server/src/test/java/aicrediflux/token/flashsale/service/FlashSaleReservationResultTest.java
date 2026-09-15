package aicrediflux.token.flashsale.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FlashSaleReservationResultTest {
    @Test
    void mapsAllLuaResultCodes() {
        assertEquals(FlashSaleReservationResult.SUCCESS, FlashSaleReservationResult.fromCode(0));
        assertEquals(FlashSaleReservationResult.NOT_READY, FlashSaleReservationResult.fromCode(1));
        assertEquals(FlashSaleReservationResult.NOT_STARTED, FlashSaleReservationResult.fromCode(2));
        assertEquals(FlashSaleReservationResult.ENDED, FlashSaleReservationResult.fromCode(3));
        assertEquals(FlashSaleReservationResult.SOLD_OUT, FlashSaleReservationResult.fromCode(4));
        assertEquals(FlashSaleReservationResult.DUPLICATE, FlashSaleReservationResult.fromCode(5));
        assertEquals(FlashSaleReservationResult.DISABLED, FlashSaleReservationResult.fromCode(6));
    }

    @Test
    void rejectsUnknownLuaResultCode() {
        assertThrows(IllegalArgumentException.class, () -> FlashSaleReservationResult.fromCode(99));
    }
}
