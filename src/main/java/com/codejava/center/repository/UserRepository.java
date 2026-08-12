package com.codejava.center.repository;

import com.codejava.center.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    /**
     * يقفل صفوف المستخدمين أثناء قرار حذف/خفض مدير.
     * بدون القفل يمكن لنافذتين أن تريا مديرين، وتحذف كلٌّ منهما واحداً في اللحظة نفسها.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u order by u.id")
    List<User> findAllForUpdate();
}
