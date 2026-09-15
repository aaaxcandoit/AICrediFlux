package aicrediflux.token.flashsale.controller;

import java.time.Instant;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import ai.yue.library.base.exception.ResultException;
import lombok.RequiredArgsConstructor;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.domain.FlashSaleRequest;
import aicrediflux.token.flashsale.domain.TokenPackage;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleRequestMapper;
import aicrediflux.token.flashsale.service.*;
import aicrediflux.token.integration.credit.CreditAccountFacade;

@RestController
@ConditionalOnProperty(name = "aicrediflux.flash-sale.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@RequestMapping("/api")
public class TokenMallController {
    private final TokenPackageService packageService;
    private final FlashSaleCampaignService campaignService;
    private final FlashSaleOrderService orderService;
    private final FlashSaleService flashSaleService;
    private final FlashSaleOrderMapper orderMapper;
    private final FlashSaleRequestMapper requestMapper;
    private final CreditAccountFacade creditAccount;
    private final Environment environment;

    @GetMapping("/token-packages")
    public Result<List<TokenPackage>> packages() {
        return R.success(packageService.listActive());
    }

    @PostMapping("/token-packages/{packageId}/orders")
    public Result<FlashSaleOrder> ordinaryOrder(HttpServletRequest request, @PathVariable long packageId) {
        return R.success(orderService.createOrdinaryOrder(userId(request), packageId));
    }

    @GetMapping("/flash-sale/campaigns")
    public Result<CampaignListResponse> campaigns() {
        return R.success(new CampaignListResponse(Instant.now(), campaignService.listAvailable()));
    }

    @PostMapping("/flash-sale/{campaignId}")
    public Result<FlashSaleSubmission> submit(HttpServletRequest request, @PathVariable long campaignId) {
        try {
            return R.success(flashSaleService.submit(userId(request), campaignId));
        } catch (FlashSaleRejectedException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @GetMapping("/flash-sale/orders")
    public Result<List<FlashSaleOrder>> orders(HttpServletRequest request) {
        return R.success(orderService.listUserOrders(userId(request)));
    }

    @GetMapping("/flash-sale/orders/{orderNo}")
    public Result<FlashSaleOrder> order(HttpServletRequest request, @PathVariable String orderNo) {
        return R.success(orderService.getUserOrder(userId(request), orderNo));
    }

    @GetMapping("/flash-sale/orders/{orderNo}/status")
    public Result<SubmissionStatus> status(HttpServletRequest request, @PathVariable String orderNo) {
        int userId = userId(request);
        FlashSaleOrder order = orderMapper.selectOwned(orderNo, userId);
        if (order != null) return R.success(new SubmissionStatus("SUCCESS", null, order));
        FlashSaleRequest pending = requestMapper.selectOwnedByOrderNo(orderNo, userId);
        if (pending == null) throw new IllegalArgumentException("秒杀请求不存在");
        String status = "FAILED".equals(pending.getProcessStatus()) ? "FAILED" : "PENDING";
        return R.success(new SubmissionStatus(status, pending.getFailReason(), null));
    }

    @PostMapping("/flash-sale/orders/{orderNo}/mock-pay")
    public Result<FlashSaleOrder> mockPay(HttpServletRequest request, @PathVariable String orderNo) {
        if (!environment.getProperty("aicrediflux.flash-sale.mock-payment-enabled", Boolean.class, false)) {
            throw new IllegalStateException("当前环境未启用模拟支付");
        }
        return R.success(orderService.mockPay(userId(request), orderNo));
    }

    @GetMapping("/flash-sale/wallet")
    public Result<WalletResponse> wallet(HttpServletRequest request) {
        return R.success(new WalletResponse(creditAccount.getBalance(userId(request))));
    }

    private int userId(HttpServletRequest request) {
        Object id = request.getAttribute("id");
        if (!(id instanceof Integer userId) || userId <= 0) throw new IllegalStateException("用户未登录");
        return userId;
    }

    public record CampaignListResponse(Instant serverTime, List<FlashSaleCampaign> campaigns) {}
    public record SubmissionStatus(String status, String reason, FlashSaleOrder order) {}
    public record WalletResponse(long balance) {}
}