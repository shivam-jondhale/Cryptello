package com.cryptonex.repository;

import com.cryptonex.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByIsDeletedFalseOrderByCreatedAtDesc();

    Page<Post> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    List<Post> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(Long authorId);

    Post findByRelatedTradeSignalIdAndType(Long signalId, Post.PostType type);
}
