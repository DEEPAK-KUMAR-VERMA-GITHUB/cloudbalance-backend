package com.cloudkeeper.cloudbalance_backend.repository.jpa;

import com.cloudkeeper.cloudbalance_backend.entity.User;
import com.cloudkeeper.cloudbalance_backend.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Filter by single role
    Page<User> findByRole(@Param("role") UserRole role, Pageable pageable);

    // Filter by active flag
    Page<User> findByActive(Boolean active, Pageable pageable);

    // Filter by active + role
    Page<User> findByActiveAndRole(@Param("active") Boolean active, @Param("role") UserRole role, Pageable pageable);

    // Case-insensitive search by name or email
    @Query(
            """
                    SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                    """
    )
    Page<User> searchByNameOrEmail(@Param("search") String search, Pageable pageable);


}
