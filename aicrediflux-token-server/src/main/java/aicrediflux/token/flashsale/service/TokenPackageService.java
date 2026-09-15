package aicrediflux.token.flashsale.service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import aicrediflux.token.flashsale.domain.TokenPackage;
import aicrediflux.token.flashsale.mapper.TokenPackageMapper;

@Service
@RequiredArgsConstructor
public class TokenPackageService {
    private final TokenPackageMapper packageMapper;
    public List<TokenPackage> listActive() { return packageMapper.selectActive(); }
    public List<TokenPackage> listAll() { return packageMapper.selectAll(); }

    @Transactional(rollbackFor = Exception.class)
    public TokenPackage save(TokenPackage pack) {
        validate(pack);
        Instant now = Instant.now();
        if (pack.getId() == null) {
            if (pack.getStatus() == null) pack.setStatus(1);
            if (pack.getSort() == null) pack.setSort(0);
            pack.setCreateTime(now);
            pack.setUpdateTime(now);
            if (packageMapper.insert(pack) != 1) throw new IllegalStateException("套餐创建失败");
        } else {
            TokenPackage old = packageMapper.selectById(pack.getId());
            if (old == null) throw new IllegalArgumentException("Token 套餐不存在");
            pack.setCreateTime(old.getCreateTime());
            pack.setUpdateTime(now);
            if (packageMapper.updateById(pack) != 1) throw new IllegalStateException("套餐更新失败");
        }
        return pack;
    }

    private void validate(TokenPackage pack) {
        if (pack.getName() == null || pack.getName().isBlank()
                || pack.getCreditAmount() == null || pack.getCreditAmount() <= 0
                || pack.getOriginalPrice() == null || pack.getOriginalPrice() < 0
                || pack.getSalePrice() == null || pack.getSalePrice() < 0) {
            throw new IllegalArgumentException("套餐名称、额度或价格无效");
        }
    }
}