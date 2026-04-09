package com.hospital.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hospital.annotation.OperationLog;
import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import com.hospital.entity.ArticleComment;
import com.hospital.service.ArticleCommentService;
import com.hospital.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/article")
public class ArticleCommentController {

    @Autowired
    private ArticleCommentService articleCommentService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/{articleId}/comments")
    public Result<IPage<ArticleComment>> listComments(
            @PathVariable("articleId") Long articleId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return articleCommentService.getCommentList(articleId, pageNum, pageSize);
    }

    @OperationLog(module = "ARTICLE_COMMENT", type = "INSERT", description = "发表评论")
    @PostMapping("/{articleId}/comment")
    public Result<ArticleComment> publishComment(
            @PathVariable("articleId") Long articleId,
            @RequestBody ArticleComment comment,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        if (comment.getParentId() == null) {
            comment.setParentId(0L);
        }
        return articleCommentService.publishComment(comment);
    }

    @OperationLog(module = "ARTICLE_COMMENT", type = "DELETE", description = "删除评论")
    @DeleteMapping("/comment/{id}")
    public Result<Void> deleteComment(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return articleCommentService.deleteComment(id, userId);
    }

    @PostMapping("/comment/like/{id}")
    public Result<Void> likeComment(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return articleCommentService.likeComment(id, userId);
    }

    @DeleteMapping("/comment/like/{id}")
    public Result<Void> unlikeComment(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return articleCommentService.unlikeComment(id, userId);
    }
}
