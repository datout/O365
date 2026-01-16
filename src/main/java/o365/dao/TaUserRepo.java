package o365.dao;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import o365.domain.TaUser;

@Repository
public interface TaUserRepo extends JpaRepository<TaUser, Integer> {
	TaUser findByUserId(String userid);
}
