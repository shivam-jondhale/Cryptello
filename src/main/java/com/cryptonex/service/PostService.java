package com.cryptonex.service;

import com.cryptonex.model.Post;
import com.cryptonex.model.TradeSignal;
import com.cryptonex.model.User;
import com.cryptonex.repository.PostRepository;
import com.cryptonex.repository.TradeSignalRepository;
import com.cryptonex.domain.USER_ROLE;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TradeSignalRepository tradeSignalRepository;

    public Post createRegularPost(User author, String content) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(content);
        post.setType(Post.PostType.REGULAR);
        return postRepository.save(post);
    }

    @Transactional
    public Post createSignalPost(User author, TradeSignal signalData, String caption) throws Exception {
        // Permission Check
        if (!author.getRoles().contains(USER_ROLE.ROLE_TRADER) &&
                !author.getRoles().contains(USER_ROLE.ROLE_VERIFIED_TRADER) &&
                !author.getRoles().contains(USER_ROLE.ROLE_ADMIN)) {
            throw new Exception("Only Traders can create signals.");
        }

        Post post = new Post();
        post.setAuthor(author);
        post.setContent(caption);
        post.setType(Post.PostType.SIGNAL);
        post = postRepository.save(post);

        signalData.setPost(post);
        signalData.setOpenedAt(LocalDateTime.now());
        signalData = tradeSignalRepository.save(signalData);

        post.setRelatedTradeSignal(signalData);
        return postRepository.save(post);
    }

    @Transactional
    public void createVictoryPostForSignal(TradeSignal signal) {
        // Idempotency Check
        Post existingVictory = postRepository.findByRelatedTradeSignalIdAndType(signal.getId(), Post.PostType.VICTORY);
        if (existingVictory != null) {
            return; // Already exists
        }

        // Update Signal Status
        signal.setStatus(TradeSignal.SignalStatus.HIT_TP);
        signal.setClosedAt(LocalDateTime.now());
        tradeSignalRepository.save(signal);

        // Create Victory Post
        Post victoryPost = new Post();
        victoryPost.setAuthor(signal.getPost().getAuthor());
        victoryPost.setType(Post.PostType.VICTORY);
        victoryPost.setContent("Target Hit! 🎯 " + signal.getCoin() + " reached take profit.");
        victoryPost.setRelatedTradeSignal(signal);

        postRepository.save(victoryPost);
        postRepository.save(victoryPost);
    }

    public Page<Post> getFeed(Pageable pageable) {
        return postRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
    }
}
