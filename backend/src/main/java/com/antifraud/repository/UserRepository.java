package com.antifraud.repository;

import com.antifraud.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
    Page<User> findByUserIdContaining(String userId, Pageable pageable);
    Page<User> findByBillNoContaining(String billNo, Pageable pageable);
}
