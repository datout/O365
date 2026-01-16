package o365.ctrl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import o365.service.DeleteSystemInfo;
import o365.service.GetSystemInfo;
import o365.service.GetDomainInfo;
import o365.service.GetLicenseInfo;
import o365.service.NoInviteRegService;
import o365.service.ResetSystemInfo;
import o365.service.UpdateSystemInfo;

@Controller
public class SystemTabCtrl {
	
	@Autowired
	private GetSystemInfo gsi;
	
	@Autowired
	private DeleteSystemInfo dsi;
	
	@Autowired
	private UpdateSystemInfo usi;
	
	@Autowired
	private ResetSystemInfo rsi;

	@Autowired
	private GetDomainInfo gdi;

	@Autowired
	private GetLicenseInfo gli;

	@Autowired
	private NoInviteRegService nirs;
	
	@RequestMapping(value = {"/tabs/system.html"})
	public String dummy() {
		return "tabs/system";
	}

	@RequestMapping(value = {"/tabs/noinvite.html"})
	public String noInvite() {
		return "tabs/noinvite";
	}
	
	@ResponseBody
	@RequestMapping(value = {"/getSystemInfo"}, method = RequestMethod.POST)
	public String getSystemInfo() {
		return gsi.getAllSystemInfo();
	}	
	
	@ResponseBody
	@RequestMapping(value = {"/deleteSystemInfo"}, method = RequestMethod.POST)
	public boolean deleteSystemInfo(@RequestParam(name="keyTy") String keyTy) {
		return dsi.deletePk(keyTy);
	}
	
	@ResponseBody
	@RequestMapping(value = {"/resetSystemInfo"}, method = RequestMethod.POST)
	public boolean resetSystemInfo() {
		return rsi.executeSql();
	}
	
	@ResponseBody
	@RequestMapping(value = {"/updateSystemInfo"}, method = RequestMethod.POST)
	public boolean updateSystemInfo(@RequestParam(name="keyTy") String keyTy,
			@RequestParam(name="cd") String cd,
			@RequestParam(name="decode") String decode) {
		return usi.updateInfo(keyTy, cd, decode);
	}


	@ResponseBody
	@RequestMapping(value = {"/getNoInviteRegConfig"}, method = RequestMethod.POST)
	public String getNoInviteRegConfig() {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("enable", nirs.isEnabled() ? "Y" : "N");
		map.put("limit", nirs.getLimit());
		map.put("used", nirs.getUsed());
		String dom = nirs.getDomain();
		if(dom != null && !dom.trim().isEmpty()) {
			map.put("domain", "@" + dom.trim());
		} else {
			map.put("domain", "");
		}
		map.put("skuId", nirs.getLicenseSkuId());
		map.put("skuIds", nirs.getLicenseSkuIds());

		// domains (already has '@' in text)
		try {
			String domainsJson = gdi.getDomains();
			JSONArray ja = JSON.parseArray(domainsJson);
			map.put("domains", ja);
		} catch (Exception e) {
			map.put("domains", new ArrayList<Object>());
		}

		// licenses combobox
		try {
			HashMap<String, Object> lmap = gli.getLicenses();
			Object obj = lmap.get("licenseVo");
			List licenses = new ArrayList();
			if (obj != null && obj instanceof List) {
				List list = (List) obj;
				for (Object o : list) {
					if (o == null) continue;
					try {
						o365.domain.LicenseInfo li = (o365.domain.LicenseInfo) o;
						HashMap<String, String> item = new HashMap<String, String>();
						item.put("id", li.getSkuId());
						String t = li.getSkuPartNumber();
						String desc = li.getSkuIdDesc();
						if (desc != null && !desc.trim().isEmpty() && t != null && !desc.equals(t)) {
							item.put("text", t + " - " + desc);
						} else {
							item.put("text", t != null ? t : li.getSkuId());
						}
						licenses.add(item);
					} catch (Exception ignore) {}
				}
			}
			map.put("licenses", licenses);
		} catch (Exception e) {
			map.put("licenses", new ArrayList<Object>());
		}

		return JSON.toJSONString(map);
	}

	@ResponseBody
	@RequestMapping(value = {"/saveNoInviteRegConfig"}, method = RequestMethod.POST)
	public boolean saveNoInviteRegConfig(@RequestParam(name="enable") String enable,
			@RequestParam(name="limit") String limit,
			@RequestParam(name="domain", required=false) String domain,
			@RequestParam(name="skuId", required=false) String skuId) {
		try {
			String en = (enable == null) ? "N" : enable.trim().toUpperCase();
			if (!"Y".equals(en)) en = "N";
			usi.updateInfo("NO_INVITE_REG_ENABLE", en, "公开注册开关（Y/N）");

			String lim = (limit == null) ? "0" : limit.trim();
			if ("".equals(lim)) lim = "0";
			usi.updateInfo("NO_INVITE_REG_LIMIT", lim, "公开注册名额");

			String dom = (domain == null) ? "" : domain.trim();
			if (!"".equals(dom) && !dom.startsWith("@")) dom = "@" + dom;
			usi.updateInfo("NO_INVITE_REG_DOMAIN", dom, "公开注册默认域名（含@）");

			String sku = (skuId == null) ? "" : skuId.trim();
			// normalize separators and remove spaces; allow multiple skuIds separated by comma
			sku = sku.replace('，', ',');
			String[] parts = sku.split(",");
			java.util.HashSet<String> seen = new java.util.HashSet<String>();
			StringBuilder sb = new StringBuilder();
			for (String p : parts) {
				if (p == null) continue;
				String x = p.trim();
				if (x.isEmpty()) continue;
				if (seen.contains(x)) continue;
				seen.add(x);
				if (sb.length() > 0) sb.append(",");
				sb.append(x);
			}
			sku = sb.toString();
			usi.updateInfo("NO_INVITE_REG_LICENSE", sku, "公开注册固定订阅skuId（GUID，逗号分隔可多选）");

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	@ResponseBody
	@RequestMapping(value = {"/resetNoInviteRegUsed"}, method = RequestMethod.POST)
	public boolean resetNoInviteRegUsed() {
		try {
			usi.updateInfo("NO_INVITE_REG_USED", "0", "公开注册已使用数量（系统自动累加）");
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

}