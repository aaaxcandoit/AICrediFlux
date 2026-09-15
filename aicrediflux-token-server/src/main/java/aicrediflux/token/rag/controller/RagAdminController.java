package aicrediflux.token.rag.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;

import aicrediflux.token.rag.domain.RagDocument;
import aicrediflux.token.rag.service.RagKnowledgeBaseService;

@RestController
@RequiredArgsConstructor
@SaCheckRole("admin")
@RequestMapping("/api/admin/rag")
public class RagAdminController {
    private final RagKnowledgeBaseService knowledgeBaseService;

    @GetMapping("/documents")
    public Result<List<RagDocument>> documents(@RequestParam(defaultValue = "200") int limit) {
        return R.success(knowledgeBaseService.listDocuments(limit));
    }

    @PostMapping("/documents")
    public Result<RagDocument> createDocument(HttpServletRequest request, @RequestBody(required = false) SaveDocumentRequest body) {
        if (body == null) {
            throw new ResultException(R.errorPrompt("文档不能为空"));
        }
        try {
            return R.success(knowledgeBaseService.saveTextDocument(userId(request),
                    new RagKnowledgeBaseService.SaveTextDocumentCommand(body.space(), body.title(), body.sourceName(), body.content(), "UPLOAD")));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @PostMapping("/documents/index-project-docs")
    public Result<List<RagDocument>> indexProjectDocs(HttpServletRequest request) {
        try {
            return R.success(knowledgeBaseService.indexProjectDocs(userId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @PostMapping("/documents/{docNo}/index")
    public Result<RagDocument> indexDocument(HttpServletRequest request, @PathVariable String docNo,
                                             @RequestBody(required = false) SaveDocumentRequest body) {
        try {
            return R.success(knowledgeBaseService.indexDocument(docNo, body == null ? null : body.content(), userId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @DeleteMapping("/documents/{docNo}")
    public Result<?> deleteDocument(@PathVariable String docNo) {
        knowledgeBaseService.deleteDocument(docNo);
        return R.success();
    }

    private int userId(HttpServletRequest request) {
        Object id = request.getAttribute("id");
        if (!(id instanceof Integer userId) || userId <= 0) {
            throw new ResultException(R.errorPrompt("用户未登录"));
        }
        return userId;
    }

    public record SaveDocumentRequest(String space, String title, String sourceName, String content) {}
}

