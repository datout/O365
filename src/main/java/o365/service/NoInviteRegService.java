package o365.service;

import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import o365.dao.TaMasterCdRepo;
import o365.domain.TaMasterCd;
import o365.service.GetDomainInfo;

/**
 * Public registration without invite code.
 *
 * Config via ta_master_cd:
 *  - NO_INVITE_REG_ENABLE: Y/N
 *  - NO_INVITE_REG_LIMIT: max registrations (int)
 *  - NO_INVITE_REG_USED: already used (int)
 *  - NO_INVITE_REG_DOMAIN: domain like x86.cc.cd (no @)
 *  - NO_INVITE_REG_LICENSE: skuId GUIDs (one or multiple; comma-separated)
 */
@Service
public class NoInviteRegService {

    public static final String K_ENABLE = "NO_INVITE_REG_ENABLE";
    public static final String K_LIMIT = "NO_INVITE_REG_LIMIT";
    public static final String K_USED = "NO_INVITE_REG_USED";
    public static final String K_DOMAIN = "NO_INVITE_REG_DOMAIN";
    public static final String K_LICENSE = "NO_INVITE_REG_LICENSE";

    @Autowired
    private TaMasterCdRepo tmc;

    @Autowired
    private CreateOfficeUser cou;

    @Autowired
    private GetDomainInfo gdi;

    public boolean isEnabled() {
        return "Y".equalsIgnoreCase(getCd(K_ENABLE, "Y"));
    }

    public int getLimit() {
        return parseInt(getCd(K_LIMIT, "0"), 0);
    }

    public int getUsed() {
        return parseInt(getCd(K_USED, "0"), 0);
    }

    public String getDomain() {
        String d = getCd(K_DOMAIN, "");
        if (d == null) return "";
        d = d.trim();
        if (d.startsWith("@")) d = d.substring(1);
        return d;
    }

    public String getLicenseSkuIds() {
        String s = getCd(K_LICENSE, "");
        if (s == null) return "";
        // support both English and Chinese comma separators
        s = s.replace('，', ',');
        String[] parts = s.split(",");
        List<String> ids = new ArrayList<String>();
        for (String p : parts) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;
            // strip quotes if user pasted JSON-like values
            if (x.startsWith("\"") && x.endsWith("\"") && x.length() > 1) {
                x = x.substring(1, x.length() - 1).trim();
            }
            if (x.isEmpty()) continue;
            if (!ids.contains(x)) ids.add(x);
        }
        if (ids.size() == 0) return "";
        // store/return normalized comma-separated GUIDs, without spaces
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    public String getLicenseSkuId() {
        // backward compatibility: first skuId only
        String s = getLicenseSkuIds();
        if (s == null) return "";
        s = s.trim();
        if (s.contains(",")) {
            s = s.split(",")[0].trim();
        }
        return s;
    }

    @CacheEvict(value = {"cacheSysInfo"}, allEntries = true)
    
    public String register(String mailNickname, String displayName, String password, String domainWithAt) {
        if (!isEnabled()) {
            return "管理员未开启公开注册";
        }

        int limit = getLimit();
        int used = getUsed();
        // limit<=0 means unlimited
        if (limit > 0 && used >= limit) {
            return "公开注册名额已用完";
        }

        // fixed license skuId
        String skuIds = getLicenseSkuIds();
        if (skuIds == null || skuIds.trim().isEmpty()) {
            return "管理员未配置公开注册订阅";
        }
        skuIds = skuIds.trim();

        // choose domain (must be verified domain from Graph)
        String chosenDomain = chooseDomain(domainWithAt); // without '@'
        if (chosenDomain == null || chosenDomain.trim().isEmpty()) {
            return "无法获取可用域名（请先在 Office配置 中配置全局并确保域名已验证）";
        }

        // create user
        String upn = mailNickname + "@" + chosenDomain;
        try {
            // double-check quota (best-effort) just before creating
            used = getUsed();
            if (limit > 0 && used >= limit) {
                return "公开注册名额已用完";
            }

            // Create user & assign fixed license
            String licenses = skuIds;
            java.util.HashMap<String, String> res = cou.createCommonUser(mailNickname, upn, displayName, licenses, password);
            String status = res.get("status");
            String msg = res.get("message");

            if ("0".equals(status)) {
                // increment used
                TaMasterCd usedCd = getOrCreate(K_USED, String.valueOf(used), "公开注册已使用数量（系统自动累加）");
                int newUsed = used + 1;
                usedCd.setCd(String.valueOf(newUsed));
                usedCd.setLastUpdateDt(new Date());
                usedCd.setLastUpdateId("o365");
                tmc.saveAndFlush(usedCd);
                return "0|" + upn;
            }
            return (msg != null && !msg.trim().isEmpty()) ? msg : "注册失败";
        } catch (Exception e) {
            e.printStackTrace();
            return "注册失败: " + e.toString();
        }
    }

    /**
     * Pick a verified domain from Graph. Input/output may contain '@' but returned value is without '@'.
     */
    private String chooseDomain(String domainWithAt) {
        List<String> domains = getVerifiedDomainsWithAt();
        String cfg = getDomain(); // without '@'
        String cfgWithAt = (cfg == null || cfg.trim().isEmpty()) ? "" : ("@" + cfg.trim());

        String d = (domainWithAt == null) ? "" : domainWithAt.trim();
        if (!d.isEmpty() && !d.startsWith("@")) d = "@" + d;

        // validate by allowed list if available
        if (domains != null && domains.size() > 0) {
            if (!d.isEmpty()) {
                for (String x : domains) {
                    if (d.equalsIgnoreCase(x)) return x.substring(1);
                }
            }
            if (!cfgWithAt.isEmpty()) {
                for (String x : domains) {
                    if (cfgWithAt.equalsIgnoreCase(x)) return x.substring(1);
                }
            }
            // fallback first verified domain
            return domains.get(0).substring(1);
        }

        // if cannot fetch domain list, fallback to config
        if (!d.isEmpty()) return d.substring(1);
        if (!cfgWithAt.isEmpty()) return cfgWithAt.substring(1);
        return "";
    }

    private List<String> getVerifiedDomainsWithAt() {
        List<String> list = new ArrayList<String>();
        try {
            String json = gdi.getDomains(); // e.g. [{text:"@domain"}]
            if (json == null || json.trim().isEmpty()) return list;
            JSONArray ja = JSON.parseArray(json);
            for (int i = 0; i < ja.size(); i++) {
                JSONObject jb = ja.getJSONObject(i);
                String text = jb.getString("text");
                if (text == null) continue;
                text = text.trim();
                if (text.isEmpty()) continue;
                if (!text.startsWith("@")) text = "@" + text;
                list.add(text);
            }
        } catch (Exception e) {
            // ignore, return empty list
        }
        return list;
    }

    private String getCd(String key, String defVal) {
        Optional<TaMasterCd> opt = tmc.findById(key);
        if (opt.isPresent()) {
            TaMasterCd cd = opt.get();
            if (cd.getCd() != null) return cd.getCd();
        }
        return defVal;
    }

    private int parseInt(String s, int defVal) {
        try { return Integer.parseInt((s == null) ? "" : s.trim()); } catch (Exception e) { return defVal; }
    }

    private TaMasterCd getOrCreate(String key, String cd, String decode) {
        Optional<TaMasterCd> opt = tmc.findById(key);
        if (opt.isPresent()) return opt.get();

        TaMasterCd t = new TaMasterCd();
        t.setKeyTy(key);
        t.setCd(cd);
        t.setDecode(decode);
        t.setCreateDt(new Date());
        t.setLastUpdateDt(new Date());
        t.setLastUpdateId("o365");
        return tmc.saveAndFlush(t);
    }
}
