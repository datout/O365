package o365.dao;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import o365.domain.TaNoInviteIpDaily;

@Repository
public interface TaNoInviteIpDailyRepo extends JpaRepository<TaNoInviteIpDaily, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from TaNoInviteIpDaily t where t.id = ?1")
	TaNoInviteIpDaily findByIdForUpdate(String id);
}