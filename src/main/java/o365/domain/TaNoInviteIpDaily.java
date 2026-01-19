package o365.domain;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Daily per-IP counter for public registration (no invite code).
 *
 * id format: yyyyMMdd|<ip>
 */
@Entity
public class TaNoInviteIpDaily {
	@Id
	private String id;
	private String day;
	private String ip;
	private int cnt;
	private Date createDt;
	private Date lastUpdateDt;

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getDay() {
		return day;
	}
	public void setDay(String day) {
		this.day = day;
	}
	public String getIp() {
		return ip;
	}
	public void setIp(String ip) {
		this.ip = ip;
	}
	public int getCnt() {
		return cnt;
	}
	public void setCnt(int cnt) {
		this.cnt = cnt;
	}
	public Date getCreateDt() {
		return createDt;
	}
	public void setCreateDt(Date createDt) {
		this.createDt = createDt;
	}
	public Date getLastUpdateDt() {
		return lastUpdateDt;
	}
	public void setLastUpdateDt(Date lastUpdateDt) {
		this.lastUpdateDt = lastUpdateDt;
	}
}
