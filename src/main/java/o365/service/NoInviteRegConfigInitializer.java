package o365.service;

import java.util.Date;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import o365.dao.TaMasterCdRepo;
import o365.domain.TaMasterCd;

/**
 * Ensure public-registration related system config keys exist.
 * Users can edit these values in "系统配置".
 */
@Service
public class NoInviteRegConfigInitializer {

    @Autowired
    private TaMasterCdRepo tmc;

    @PostConstruct
    public void init() {
        ensure(NoInviteRegService.K_ENABLE, "N", "公开注册(无邀请码)开关：Y启用/N关闭");
        ensure(NoInviteRegService.K_LIMIT, "0", "公开注册(无邀请码)可注册数量上限(整数)");
        ensure(NoInviteRegService.K_USED, "0", "公开注册(无邀请码)已使用数量（系统自动累加）");
        ensure(NoInviteRegService.K_DOMAIN, "", "公开注册(无邀请码)默认域名(不含@)，例如 x86.cc.cd");
        ensure(NoInviteRegService.K_LICENSE, "", "公开注册(无邀请码)默认订阅skuId(GUID，可逗号分隔多选)，固定分配");
    }

    private void ensure(String key, String cd, String decode) {
        if (tmc.findById(key).isPresent()) {
            return;
        }
        TaMasterCd t = new TaMasterCd();
        t.setKeyTy(key);
        t.setCd(cd);
        t.setDecode(decode);
        t.setCreateDt(new Date());
        t.setLastUpdateDt(new Date());
        t.setLastUpdateId("o365");
        tmc.saveAndFlush(t);
    }
}
