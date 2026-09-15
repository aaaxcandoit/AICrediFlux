package aicrediflux.token.flashsale.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import cn.dev33.satoken.annotation.SaCheckRole;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import lombok.RequiredArgsConstructor;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.domain.TokenPackage;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;
import aicrediflux.token.flashsale.service.FlashSaleCampaignService;
import aicrediflux.token.flashsale.service.TokenPackageService;

@RestController
@ConditionalOnProperty(name = "aicrediflux.flash-sale.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@SaCheckRole("admin")
@RequestMapping("/api/admin")
public class TokenMallAdminController {
    private final TokenPackageService packageService;
    private final FlashSaleCampaignService campaignService;
    private final FlashSaleOrderMapper orderMapper;

    @GetMapping("/token-packages")
    public Result<List<TokenPackage>> packages() { return R.success(packageService.listAll()); }

    @PostMapping("/token-packages")
    public Result<TokenPackage> createPackage(@RequestBody TokenPackage pack) {
        pack.setId(null);
        return R.success(packageService.save(pack));
    }

    @PutMapping("/token-packages/{id}")
    public Result<TokenPackage> updatePackage(@PathVariable long id, @RequestBody TokenPackage pack) {
        pack.setId(id);
        return R.success(packageService.save(pack));
    }

    @GetMapping("/flash-sale")
    public Result<List<FlashSaleCampaign>> campaigns() { return R.success(campaignService.listAll()); }

    @PostMapping("/flash-sale")
    public Result<FlashSaleCampaign> createCampaign(@RequestBody FlashSaleCampaign campaign) {
        return R.success(campaignService.create(campaign));
    }

    @PutMapping("/flash-sale/{id}")
    public Result<FlashSaleCampaign> updateCampaign(@PathVariable long id, @RequestBody FlashSaleCampaign campaign) {
        return R.success(campaignService.update(id, campaign));
    }

    @PostMapping("/flash-sale/{id}/publish")
    public Result<FlashSaleCampaign> publish(@PathVariable long id) {
        return R.success(campaignService.publish(id));
    }

    @PostMapping("/flash-sale/{id}/reconcile")
    public Result<FlashSaleCampaign> reconcile(@PathVariable long id) {
        return R.success(campaignService.reconcile(id));
    }

    @GetMapping("/flash-sale/orders")
    public Result<List<FlashSaleOrder>> orders(@RequestParam(defaultValue = "200") int limit) {
        return R.success(orderMapper.selectRecent(Math.min(Math.max(limit, 1), 1000)));
    }
}