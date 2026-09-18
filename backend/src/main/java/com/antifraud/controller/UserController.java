package com.antifraud.controller;

import com.antifraud.entity.User;
import com.antifraud.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

/** 用户画像接口：暴露运营商「用户数据」静态画像（users 表） */
@RestController @RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepo;

    public UserController(UserRepository userRepo) { this.userRepo = userRepo; }

    @GetMapping
    public Page<User> list(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int pageSize,
                           @RequestParam(required = false) String q) {
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), pageSize, Sort.by("userId"));
        if (q != null && !q.isBlank()) {
            return userRepo.findByUserIdContaining(q, pr);
        }
        return userRepo.findAll(pr);
    }

    @GetMapping("/{userId}")
    public User get(@PathVariable String userId) {
        return userRepo.findById(userId).orElse(null);
    }
}
