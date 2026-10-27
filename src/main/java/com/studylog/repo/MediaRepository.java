package com.studylog.repo;

import com.studylog.model.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findBySha1(String sha1);
}
