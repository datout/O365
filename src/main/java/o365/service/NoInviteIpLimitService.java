package o365.service;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import o365.dao.TaNoInviteIpDailyRepo;
import o365.domain.TaNoInviteIpDaily;

/**
 * Per-IP per-day limit helper for public registration (no invite code).
 */
@Service
public class NoInviteIpLimitService {

	@Autowired
	private TaNoInviteIpDailyRepo repo;

	private String todayYmd() {
		return new SimpleDateFormat("yyyyMMdd").format(new Date());
	}

	@Transactional
	public boolean tryAcquire(String ip, int limit) {
		if (limit <= 0) return true;
		if (ip == null) return true;
		ip = ip.trim();
		if (ip.isEmpty()) return true;

		String day = todayYmd();
		String id = day + "|" + ip;

		TaNoInviteIpDaily row = repo.findByIdForUpdate(id);
		if (row == null) {
			TaNoInviteIpDaily t = new TaNoInviteIpDaily();
			t.setId(id);
			t.setDay(day);
			t.setIp(ip);
			t.setCnt(1);
			t.setCreateDt(new Date());
			t.setLastUpdateDt(new Date());
			try {
				repo.saveAndFlush(t);
				return true;
			} catch (DataIntegrityViolationException ex) {
				// Possible concurrent insert; fall through to re-fetch & update.
			}
			row = repo.findByIdForUpdate(id);
		}

		if (row == null) {
			// Unexpected; fail open.
			return true;
		}

		int cnt = row.getCnt();
		if (cnt >= limit) {
			return false;
		}
		row.setCnt(cnt + 1);
		row.setLastUpdateDt(new Date());
		repo.saveAndFlush(row);
		return true;
	}

	@Transactional
	public void release(String ip) {
		if (ip == null) return;
		ip = ip.trim();
		if (ip.isEmpty()) return;

		String day = todayYmd();
		String id = day + "|" + ip;
		TaNoInviteIpDaily row = repo.findByIdForUpdate(id);
		if (row == null) return;

		int cnt = row.getCnt();
		if (cnt <= 1) {
			repo.delete(row);
			return;
		}
		row.setCnt(cnt - 1);
		row.setLastUpdateDt(new Date());
		repo.saveAndFlush(row);
	}
}
