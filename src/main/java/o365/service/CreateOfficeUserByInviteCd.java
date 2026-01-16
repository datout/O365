package o365.service;

import java.util.Date;
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
import org.springframework.web.client.HttpClientErrorException.BadRequest;

import com.alibaba.fastjson.JSON;

import o365.dao.TaInviteInfoRepo;
import o365.dao.TaOfficeInfoRepo;
import o365.domain.OfficeUser;
import o365.domain.TaInviteInfo;
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
public class CreateOfficeUserByInviteCd {
	private RestTemplate restTemplate = new RestTemplate();
	
	@Autowired
	private TaOfficeInfoRepo repo;
	
	@Autowired
	private ValidateAppInfo vai;
	
	@Autowired
	private TaInviteInfoRepo tii;
	
	@Autowired
	private GetOrganizationInfo goi;
	
	@Value("${UA}")
    private String ua;
	
	@CacheEvict(value= {"cacheOfficeUser","cacheInviteInfo","cacheOfficeUserSearch"}, allEntries = true)
	public String createCommonUser(String mailNickname, String displayName, String inviteCd, String password){
		String resultMsg = "失败";
		Optional<TaInviteInfo> opt = tii.findById(inviteCd);
		if(opt.isPresent()) {
			TaInviteInfo tiiDo = opt.get();
			
			Date currentDt = new Date();
			Date startDt = tiiDo.getStartDt();
			Date endDt = tiiDo.getEndDt();
			
			if(startDt!=null&&startDt.after(currentDt)) {
				resultMsg = "此邀请码尚未生效";
				return resultMsg;
			}
			
			if(endDt!=null&&endDt.before(currentDt)) {
				resultMsg = "此邀请码已过期";
				return resultMsg;
			}
			
			if("1".equals(tiiDo.getInviteStatus())){
				//update the invite code to in-progress
				tiiDo.setInviteStatus("2");
				tii.save(tiiDo);
				
				Optional<TaOfficeInfo> opt1 = repo.findById(tiiDo.getSeqNo());
				if(opt1.isPresent()) {
					//get info
					TaOfficeInfo ta = opt1.get();
					String accessToken = "";
					if(vai.checkAndGet(ta.getTenantId(), ta.getAppId(), ta.getSecretId())) {
						accessToken = vai.getAccessToken();
					}
					
					if(!"".equals(accessToken)) {
						String userPrincipalName = mailNickname + tiiDo.getSuffix();
						OfficeUser ou = new OfficeUser();
						ou.setMailNickname(mailNickname);
						ou.setUserPrincipalName(userPrincipalName);
						ou.setDisplayName(displayName);
						ou.getPasswordProfile().setPassword(password);
						ou.getPasswordProfile().setForceChangePasswordNextSignIn(true);
						//set usage location
						ou.setUsageLocation(goi.getUsageLocation(accessToken));
						String createUserJson = JSON.toJSONString(ou);
						
						//1. create user
						String endpoint = "https://graph.microsoft.com/v1.0/users";
						HttpHeaders headers = new HttpHeaders();
						headers.set(HttpHeaders.USER_AGENT, ua);
						headers.add("Authorization", "Bearer "+accessToken);
						headers.setContentType(MediaType.APPLICATION_JSON);
						HttpEntity<String> requestEntity = new HttpEntity<String>(createUserJson, headers);
						String createdUserId = null;
						try {
							ResponseEntity<String> response= restTemplate.postForEntity(endpoint, requestEntity, String.class);
							if(response.getStatusCodeValue()==201) {
								String createRespBody = response.getBody();
								try {
									createdUserId = JSON.parseObject(createRespBody).getString("id");
									System.out.println("Created user id=" + createdUserId);
								} catch (Exception ex) {
									System.out.println("Failed to parse created user id: " + ex);
								}
								System.out.println( "成功创建用户："+ou.getUserPrincipalName());
								Thread.sleep(500);
							}
							else {
								tiiDo.setResult("创建用户出错 1");
								tiiDo.setInviteStatus("4");
								tii.save(tiiDo);
								resultMsg = "创建用户出错";
								return resultMsg;
							}
						}
						catch (Exception e) {
							if(e instanceof BadRequest) {
								String responeBody = ((BadRequest) e).getResponseBodyAsString();
								System.out.println(responeBody);
								if(responeBody.indexOf("same value")>=0) {
									tiiDo.setResult("此前缀已存在，请选择一个另外的前缀");
									tiiDo.setInviteStatus("1");
									tii.save(tiiDo);
									resultMsg = "此前缀已存在，请选择一个另外的前缀";
									return resultMsg;
								}
								else {
									tiiDo.setResult("创建用户出错 2");
									tiiDo.setInviteStatus("4");
									tii.save(tiiDo);
									resultMsg = "创建用户出错";
									return resultMsg;
								}
							}
							else {
								tiiDo.setResult("创建用户出错 3");
								tiiDo.setInviteStatus("4");
								tii.save(tiiDo);
								resultMsg = "创建用户出错";
								return resultMsg;
							}
						}
						
						//2. assign license
						String licenses = tiiDo.getLicenses();
						if(licenses!=null&&!"".equals(licenses)) {
							System.out.println("开始分配订阅："+licenses);
							String acs [] = licenses.split(",");
							for (String license : acs) {
							    // Validate skuId is GUID (defensive)
							    if (license == null || license.trim().isEmpty() || !license.trim().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
							        System.out.println("Invalid skuId (not GUID): " + license);
							        tiiDo.setResult("无效订阅skuId:" + license);
							        tiiDo.setInviteStatus("4");
							        tii.save(tiiDo);
							        resultMsg = "分配订阅时出错";
							        return resultMsg;
							    }
								String licenseJson = "{\"addLicenses\":[{\"disabledPlans\":[],\"skuId\":\"" + license + "\"}],\"removeLicenses\":[]}";
								
								String userRef = (createdUserId != null && !createdUserId.isEmpty()) ? createdUserId : ou.getUserPrincipalName();
								endpoint = "https://graph.microsoft.com/v1.0/users/" + userRef + "/assignLicense";
								System.out.println("AssignLicense target=" + userRef + " skuId=" + license);
								
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
								if (assigned) {
									tiiDo.setResult(ou.getUserPrincipalName()+"|"+password);
									tiiDo.setInviteStatus("3");
									tii.save(tiiDo);
									resultMsg = "0|"+ou.getUserPrincipalName();
								} else {
									tiiDo.setResult("分配订阅时出错");
									tiiDo.setInviteStatus("4");
									tii.save(tiiDo);
									resultMsg = "分配订阅时出错";
									return resultMsg;
								}
							}
						}
						else {
							tiiDo.setResult(ou.getUserPrincipalName()+"|"+password);
							tiiDo.setInviteStatus("3");
							tii.save(tiiDo);
							resultMsg = "0|"+ou.getUserPrincipalName();
						}
					}
					else {
						tiiDo.setResult("无效的全局");
						tiiDo.setInviteStatus("4");
						tii.save(tiiDo);
						resultMsg = "无效的全局";
					}
				}
				else {
					tiiDo.setResult("不存在的全局");
					tiiDo.setInviteStatus("4");
					tii.save(tiiDo);
					resultMsg = "不存在的全局";
				}
			}
			else if("2".equals(tiiDo.getInviteStatus())){
				resultMsg = "此邀请码正被使用中";
			}
			else if("3".equals(tiiDo.getInviteStatus())){
				resultMsg = "此邀请码已使用";
			}
			else if("4".equals(tiiDo.getInviteStatus())){
				resultMsg = "此邀请码使用出现错误";
			}
			else {
				resultMsg = "无效的邀请码状态";
			}
		}
		else {
			resultMsg = "无效的邀请码";
		}
		
		return resultMsg;
	}
}
