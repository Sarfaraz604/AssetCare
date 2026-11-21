package com.assetcare.repository;

import com.assetcare.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByUsername(String username);

  List<AppUser> findByRoleOrderByDisplayName(Role role);
}
