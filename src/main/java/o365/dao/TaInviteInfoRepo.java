package o365.dao;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import o365.domain.TaInviteInfo;

@Repository
public interface TaInviteInfoRepo extends JpaRepository<TaInviteInfo, String> {
}
