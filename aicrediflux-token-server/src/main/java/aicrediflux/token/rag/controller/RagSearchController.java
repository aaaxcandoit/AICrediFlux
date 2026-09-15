package aicrediflux.token.rag.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;

import aicrediflux.token.rag.service.RagRetrievalResult;
import aicrediflux.token.rag.service.RagRetrievalService;

@RestController
@RequiredArgsConstructor
@SaCheckLogin
@RequestMapping("/api/agent/rag")
public class RagSearchController {
    private final RagRetrievalService retrievalService;

    @GetMapping("/search")
    public Result<RagRetrievalResult> search(HttpServletRequest request,
                                             @RequestParam String q,
                                             @RequestParam(required = false) List<String> space,
                                             @RequestParam(required = false) Integer topK) {
        return R.success(retrievalService.search(userId(request), q, space, topK == null ? 0 : topK));
    }

    private int userId(HttpServletRequest request) {
        Object id = request.getAttribute("id");
        if (!(id instanceof Integer userId) || userId <= 0) {
            throw new ResultException(R.errorPrompt("用户未登录"));
        }
        return userId;
    }
}
