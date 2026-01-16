package o365.service;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.alibaba.fastjson.JSON;

import o365.dao.TaMasterCdRepo;
import o365.dao.TaOfficeInfoRepo;
import o365.domain.OfficeUser;
import o365.domain.TaMasterCd;
import o365.domain.TaOfficeInfo;

/*
 * {
	  "addLicenses": [
	    {
	      "disabledPlans": [ ],
	      "skuId": "guid",
	    }
	  ],
	  "removeLicenses": [ ]
   }
 */
@Service
public class CreateOfficeUser {
	private RestTemplate restTemplate = new RestTemplate();
	
	@Autowired
	private TaOfficeInfoRepo repo;
	
	@Autowired
	private ValidateAppInfo vai;
	
	@Autowired
	private TaMasterCdRepo tmr;
	
	@Autowired
	private GetOrganizationInfo goi;
	
	@Value("${UA}")
    private String ua;
	
	@CacheEvict(value= {"cacheOfficeUser","cacheOfficeUserSearch","cacheLicense"}, allEntries = true)
	public HashMap<String, String> createCommonUser(String mailNickname, String userPrincipalName, String displayName, String licenses, String userPwd){
		HashMap<String, String> map = new HashMap<String, String>();
		String forceInd = "Y";
		Optional<TaMasterCd> opt = tmr.findById("FORCE_CHANGE_PASSWORD");
		if(opt.isPresent()) {
			TaMasterCd cd = opt.get();
			forceInd = cd.getCd();
		}

		OfficeUser ou = new OfficeUser();
		ou.setMailNickname(mailNickname);
		ou.setUserPrincipalName(userPrincipalName);
		ou.setDisplayName(displayName);
		ou.getPasswordProfile().setPassword(userPwd);
		if(!"Y".equals(forceInd)) {
			ou.getPasswordProfile().setForceChangePasswordNextSignIn(false);
		}
		String message = "";
		
		//get info
		List<TaOfficeInfo> list = repo.findBySelected("是");
		if(list!=null&&list.size()>0) {
			TaOfficeInfo ta = list.get(0);
			String accessToken = "";
			if(vai.checkAndGet(ta.getTenantId(), ta.getAppId(), ta.getSecretId())) {
				accessToken = vai.getAccessToken();
			}
			
			if(!"".equals(accessToken)) {
				//set usage location
				ou.setUsageLocation(goi.getUsageLocation(accessToken));
				String createUserJson = JSON.toJSONString(ou);
				
				String endpoint = "https://graph.microsoft.com/v1.0/users";
				HttpHeaders headers = new HttpHeaders();
				headers.set(HttpHeaders.USER_AGENT, ua);
				headers.add("Authorization", "Bearer "+accessToken);
				headers.setContentType(MediaType.APPLICATION_JSON);
				HttpEntity<String> requestEntity = new HttpEntity<String>(createUserJson, headers);
				try {
					ResponseEntity<String> response= restTemplate.postForEntity(endpoint, requestEntity, String.class);
					if(response.getStatusCodeValue()==201) {
						String createRespBody = response.getBody();
						String createdUserId = null;
						try {
							createdUserId = JSON.parseObject(createRespBody).getString("id");
							System.out.println("Created user id=" + createdUserId);
						} catch (Exception ex) {
							System.out.println("Failed to parse created user id: " + ex);
						}
						message = "成功创建用户"+ou.getUserPrincipalName()+"<br>";
						map.put("status", "0");
						map.put("message", message);
						System.out.println( "成功创建用户："+ou.getUserPrincipalName());
						
						if(licenses!=null&&!"".equals(licenses)) {
							Thread.sleep(200);
							System.out.println("开始分配订阅："+licenses);
							String acs [] = licenses.split(",");
							for (String license : acs) {
							    String lic = (license == null) ? "" : license.trim();
							    // Validate skuId is GUID (defensive)
							    if (lic.isEmpty() || !lic.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
							        System.out.println("Invalid skuId (not GUID): " + lic);
							        message = message + "无效订阅skuId：" + lic + "<br>";
							        map.put("message", message);
							        continue;
							    }
								String licenseJson = "{\"addLicenses\":[{\"disabledPlans\":[],\"skuId\":\"" + lic + "\"}],\"removeLicenses\":[]}";
								
								String userRef = (createdUserId != null && !createdUserId.isEmpty()) ? createdUserId : ou.getUserPrincipalName();
								endpoint = "https://graph.microsoft.com/v1.0/users/" + userRef + "/assignLicense";
								System.out.println("AssignLicense target=" + userRef + " skuId=" + lic);
								
								HttpHeaders headers2 = new HttpHeaders();
								headers2.set(HttpHeaders.USER_AGENT, ua);
								headers2.add("Authorization", "Bearer "+accessToken);
								headers2.setContentType(MediaType.APPLICATION_JSON);
								HttpEntity<String> requestEntity2 = new HttpEntity<String>(licenseJson, headers2);
								
								int[] waits = new int[]{1000, 2000, 4000, 8000, 16000, 16000};
								boolean assigned = false;
								for (int attempt = 0; attempt < waits.length; attempt++) {
									try {
										ResponseEntity<String> response2 = restTemplate.postForEntity(endpoint, requestEntity2, String.class);
										if (response2.getStatusCodeValue() == 200) {
											assigned = true;
											message = message + "成功分配订阅：" + lic + "<br>";
											map.put("message", message);
											break;
										} else {
											System.out.println("AssignLicense unexpected status=" + response2.getStatusCodeValue());
											System.out.println("AssignLicense body=" + response2.getBody());
											break;
										}
									} catch (org.springframework.web.client.HttpStatusCodeException he) {
										int s = he.getRawStatusCode();
										System.out.println("AssignLicense try=" + (attempt + 1) + " status=" + s);
										System.out.println("AssignLicense body=" + he.getResponseBodyAsString());
										if ((s == 404 || s == 429 || s >= 500) && attempt < waits.length - 1) {
											try { Thread.sleep(waits[attempt]); } catch (Exception ignore) {}
											continue;
										}
										break;
									} catch (Exception e) {
										e.printStackTrace();
										break;
									}
								}
								if (!assigned) {
									message = message + "分配订阅：" + lic + " 时失败<br>";
									map.put("message", message);
								}
							}
						}
						
						Optional<TaMasterCd> cgfu =  tmr.findById("CREATE_GRP_FOR_USER");
						if(cgfu.isPresent()) {
							if("Y".equals(cgfu.get().getCd())) {
								Thread.sleep(200);
								createGroupForUser(mailNickname, userPrincipalName, accessToken);
							}
						}
						
					}
					else {
						map.put("status", "1");
						map.put("message", "失败，创建用户未能获得预期的返回值201");
					}
				}
				catch (Exception e) {
					e.printStackTrace();
					map.put("status", "1");
					map.put("message", "无法创建用户 "+e.toString());
				}
			}
			else {
				map.put("status", "1");
				map.put("message", "获取token失败，请确认全局的有效性");
			}
		}
		else {
			map.put("status", "1");
			map.put("message", "请先选择一个全局");
		}
		
		return map;
	}
	
	/*
	 * Json for create group (when create group, SP site will be created automatically later)
		{
		    "expirationDateTime": null,
		    "groupTypes": [
		        "Unified"
		    ],
		    "mailEnabled": false,
		    "mailNickname": mailNickname,
		    "securityEnabled": false,
		    "visibility": "private",
			"owners@odata.bind": [
				"https://graph.microsoft.com/v1.0/users/userPrincipalName"
			]
		}
	 */
	public void createGroupForUser(String mailNickname, String userPrincipalName, String accessToken) {
		String json = "{\"displayName\": \""+mailNickname+"\",\"expirationDateTime\": null,\"groupTypes\": [\"Unified\"],\"mailEnabled\": false,\"mailNickname\": \""+mailNickname+"\",\"securityEnabled\": false,\"visibility\": \"private\",\"owners@odata.bind\": [\"https://graph.microsoft.com/v1.0/users/"+userPrincipalName+"\"]}";
		
		String endpoint = "https://graph.microsoft.com/v1.0/groups";
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.USER_AGENT, ua);
		headers.add("Authorization", "Bearer "+accessToken);
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> requestEntity = new HttpEntity<String>(json, headers);
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(endpoint, requestEntity, String.class);
			if(response.getStatusCodeValue()==201) {
				System.out.println("成功创建Group:"+mailNickname);
				response.getBody();
			}
			else {
				System.out.println("fail to create the group for user "+userPrincipalName);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
