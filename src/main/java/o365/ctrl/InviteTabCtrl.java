package o365.ctrl;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import o365.dao.TaInviteInfoRepo;
import o365.domain.LicenseInfo;
import o365.domain.TaInviteInfo;
import o365.service.GetDomainInfo;
import o365.service.CreateOfficeUserByInviteCd;
import o365.service.DeleteInviteInfo;
import o365.service.ExportInvites;
import o365.service.GetInviteInfo;
import o365.service.GetLicenseInfo;
import o365.service.MassCreateInviteCd;
import o365.service.NoInviteRegService;

@Controller
public class InviteTabCtrl {
	
	@Autowired
	private GetInviteInfo gii;
	
	@Autowired
	private GetLicenseInfo gli;
	
	@Autowired
	private MassCreateInviteCd mci;
	
	@Autowired
	private DeleteInviteInfo dii;
	
	@Autowired
	private CreateOfficeUserByInviteCd coubi;
	
	@Autowired
	private ExportInvites ei;

	@Autowired
	private NoInviteRegService nirs;

	@Autowired
	private GetDomainInfo gdi;

	@Autowired
	private TaInviteInfoRepo tii;

	
	@RequestMapping(value = {"/tabs/invite.html"})
	public String dummy() {
		return "tabs/invite";
	}
	
	@RequestMapping(value = {"/tabs/dialogs/createInviteCd.html"})
	public String dummy2(HttpServletRequest req) {
		Object tmp2 = req.getSession().getAttribute("licenseVo");
		if(tmp2==null) {
			HashMap<String, Object> map2 = gli.getLicenses();
			List<LicenseInfo> vo = new ArrayList<LicenseInfo>();
			Object obj = map2.get("licenseVo");
			if(obj!=null) {
				vo = (List<LicenseInfo>)obj;
			}
			req.getSession().setAttribute("licenseVo", vo);
		}
		else {
			System.out.println("licenseVo already exist,skip to get");
		}
		return "tabs/dialogs/createInviteCd";
	}
	
	@RequestMapping(value = {"/refer", "/refer.html"})
	public String refer(Model model) {

		boolean enabled = nirs.isEnabled();
		model.addAttribute("noInviteEnabled", enabled);
		if (enabled) {
			int limit = nirs.getLimit();
			int used = nirs.getUsed();
			int remain = limit - used;
			if (remain < 0) remain = 0;

			// domains from Graph (verified) - text already contains '@'
			List<String> domains = new ArrayList<String>();
			try {
				String domainsJson = gdi.getDomains();
				JSONArray ja = JSON.parseArray(domainsJson);
				for (int i = 0; i < ja.size(); i++) {
					JSONObject jb = ja.getJSONObject(i);
					String d = jb.getString("text");
					if (d != null && !d.trim().isEmpty()) {
						d = d.trim();
						if (!d.startsWith("@")) d = "@" + d;
						domains.add(d);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			model.addAttribute("noInviteDomains", domains);

			String defaultDomain = nirs.getDomain(); // without '@'
			if (defaultDomain != null) defaultDomain = defaultDomain.trim();
			String defaultDomainWithAt = (defaultDomain == null || defaultDomain.isEmpty()) ? "" : ("@" + defaultDomain);
			// if config default not present in list, fallback to first domain in list
			if ((defaultDomainWithAt == null || defaultDomainWithAt.isEmpty()) && domains != null && domains.size() > 0) {
				defaultDomainWithAt = domains.get(0);
			}
			if (domains != null && domains.size() > 0 && defaultDomainWithAt != null && !defaultDomainWithAt.isEmpty()) {
				boolean found = false;
				for (String d : domains) {
					if (defaultDomainWithAt.equals(d)) { found = true; break; }
				}
				if (!found) defaultDomainWithAt = domains.get(0);
			}
			model.addAttribute("noInviteDomainDefault", defaultDomainWithAt);

			model.addAttribute("noInviteSkuId", nirs.getLicenseSkuId());
			model.addAttribute("noInviteLimit", limit);
			model.addAttribute("noInviteUsed", used);
			model.addAttribute("noInviteRemain", remain);
		}
		return "refer";
	}

	@ResponseBody
	@RequestMapping(value = {"/createUserNoInvite"}, method = RequestMethod.POST)
	public String createUserNoInvite(@RequestParam(name="mailNickname") String mailNickname,
			@RequestParam(name="displayName") String displayName,
			@RequestParam(name="domain", required=false) String domain,
			@RequestParam(name="password") String password) {
		return nirs.register(mailNickname, displayName, password, domain);
	}
	
	@ResponseBody
	@RequestMapping(value = {"/getInvite"})
	public String getInvite(String page, String rows) {
		int intPage = 1;
		int intRows = 10;
		try {
			intPage = Integer.valueOf(page);
		}
		catch (Exception e) {
			System.out.println("Invalid page, force it to 1");
		}
		try {
			intRows = Integer.valueOf(rows);
		}
		catch (Exception e) {
			System.out.println("Invalid row, force it to 10");
		}
		
		return gii.getAllInviteInfo(intRows, intPage);
	}
	
	@ResponseBody
	@RequestMapping(value = {"/massCreateInviteCd"})
	public String massCreateInviteCd(@RequestParam(name="count") String countStr,
			@RequestParam(name="licenses") String licenses,
			@RequestParam(name="domain") String domain,
			@RequestParam(name="startDt",required = false) String startDt,
			@RequestParam(name="endDt",required = false) String endDt) {
		
		System.out.println("startDt:"+startDt+" endDt:"+endDt);
		int count = 10;
		try {
			count = Integer.parseInt(countStr);
		}
		catch (Exception e) {}
		
		return mci.create(count, licenses, startDt, endDt, domain);
	}
	
	@ResponseBody
	@RequestMapping(value = {"/delInviteCds"})
	public String deleteInviteInfo(@RequestParam(name="uuids") String uuids) {
		dii.deleteInviteCd(uuids);
		return "已删除";
	}
	
	@ResponseBody
	@RequestMapping(value = {"/createUserByInviteCd"})
	public String createUserByInviteCd(@RequestParam(name="mailNickname") String mailNickname,
			@RequestParam(name="displayName") String displayName,
			@RequestParam(name="inviteCd") String inviteCd,
			@RequestParam(name="password") String password) {

		return coubi.createCommonUser(mailNickname, displayName, inviteCd, password);
	}
	
	@RequestMapping(value = {"/exportInvites"}, method = RequestMethod.GET)
	public ResponseEntity<FileSystemResource> exportApps(){
		ei.exportInvite();
		
		return export(new File("export_invite_info.csv"));
	}
	
	public ResponseEntity<FileSystemResource> export(File file) { 
		HttpHeaders headers = new HttpHeaders();
	    headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
	    headers.add("Content-Disposition", "attachment; filename=export_invite_info.csv");
	    headers.add("Pragma", "no-cache");
	    headers.add("Expires", "0");
	    headers.add("Last-Modified", new Date().toString());
	    headers.add("ETag", String.valueOf(System.currentTimeMillis()));
	 
	    return ResponseEntity.ok().headers(headers) .contentLength(file.length()) .contentType(MediaType.parseMediaType("text/csv")) .body(new FileSystemResource(file));
	}
	


	// Public API: get suffix (domain) decided by invite code, for UI display.
	@ResponseBody
	@RequestMapping(value = {"/public/inviteSuffix"}, method = RequestMethod.GET)
	public String publicInviteSuffix(@RequestParam(name="inviteCd") String inviteCd) {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("status", "1");
		map.put("suffix", "");
		try {
			if (inviteCd == null) return JSON.toJSONString(map);
			inviteCd = inviteCd.trim();
			if ("".equals(inviteCd)) return JSON.toJSONString(map);
			java.util.Optional<TaInviteInfo> opt = tii.findById(inviteCd);
			if (opt.isPresent()) {
				String suffix = opt.get().getSuffix();
				if (suffix == null) suffix = "";
				suffix = suffix.trim();
				if (!"".equals(suffix) && !suffix.startsWith("@")) suffix = "@" + suffix;
				map.put("status", "0");
				map.put("suffix", suffix);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return JSON.toJSONString(map);
	}

}