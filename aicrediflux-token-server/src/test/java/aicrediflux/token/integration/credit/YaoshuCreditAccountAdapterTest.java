package aicrediflux.token.integration.credit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import aicrediflux.token.flashsale.mapper.CreditGrantLogMapper;

class YaoshuCreditAccountAdapterTest {

    @Test
    void duplicateBusinessNumberCreditsWalletOnlyOnce() {
        CreditAccountMapper accountMapper = mock(CreditAccountMapper.class);
        CreditGrantLogMapper grantMapper = mock(CreditGrantLogMapper.class);
        when(grantMapper.insertPending(3, 3_000_000_000L, "flash-sale:ORDER-1", "ORDER-1")).thenReturn(1, 0);
        when(accountMapper.increaseQuota(3, 3_000_000_000L)).thenReturn(1);
        when(grantMapper.markSuccess("flash-sale:ORDER-1")).thenReturn(1);
        YaoshuCreditAccountAdapter adapter = new YaoshuCreditAccountAdapter(accountMapper, grantMapper);

        adapter.credit(3, 3_000_000_000L, "flash-sale:ORDER-1");
        adapter.credit(3, 3_000_000_000L, "flash-sale:ORDER-1");

        verify(accountMapper, times(1)).increaseQuota(3, 3_000_000_000L);
        verify(grantMapper, times(1)).markSuccess("flash-sale:ORDER-1");
    }

    @Test
    void balanceUsesLongQuota() {
        CreditAccountMapper accountMapper = mock(CreditAccountMapper.class);
        CreditGrantLogMapper grantMapper = mock(CreditGrantLogMapper.class);
        when(accountMapper.selectQuota(3)).thenReturn(5_000_000_000L);

        YaoshuCreditAccountAdapter adapter = new YaoshuCreditAccountAdapter(accountMapper, grantMapper);
        assertEquals(5_000_000_000L, adapter.getBalance(3));
    }
}
