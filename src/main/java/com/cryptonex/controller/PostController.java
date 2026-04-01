package com.cryptonex.controller;

import com.cryptonex.model.Post;
import com.cryptonex.model.TradeSignal;
import com.cryptonex.model.User;
import com.cryptonex.request.CreatePostRequest;
import com.cryptonex.request.CreateSignalRequest;
import com.cryptonex.service.PostService;
import com.cryptonex.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private UserService userService;

    @PostMapping("/regular")
    public ResponseEntity<Post> createRegularPost(
            @RequestHeader("Authorization") String jwt,
            @RequestBody CreatePostRequest request) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);
        Post post = postService.createRegularPost(user, request.getContent());
        return new ResponseEntity<>(post, HttpStatus.CREATED);
    }

    @PostMapping("/signal")
    public ResponseEntity<Post> createSignalPost(
            @RequestHeader("Authorization") String jwt,
            @RequestBody CreateSignalRequest request) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);

        TradeSignal signal = new TradeSignal();
        signal.setCoin(request.getCoin());
        signal.setEntryRangeMin(request.getEntryRangeMin());
        signal.setEntryRangeMax(request.getEntryRangeMax());
        signal.setTakeProfits(request.getTakeProfits());
        signal.setStopLoss(request.getStopLoss());
        signal.setDirection(request.getDirection());
        signal.setLeverage(request.getLeverage());
        signal.setRiskRating(request.getRiskRating());
        signal.setStrategyType(request.getStrategyType());

        Post post = postService.createSignalPost(user, signal, request.getCaption());
        return new ResponseEntity<>(post, HttpStatus.CREATED);
    }

    @GetMapping("/feed")
    public ResponseEntity<Page<Post>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Post> feed = postService.getFeed(pageable);
        return new ResponseEntity<>(feed, HttpStatus.OK);
    }
}
