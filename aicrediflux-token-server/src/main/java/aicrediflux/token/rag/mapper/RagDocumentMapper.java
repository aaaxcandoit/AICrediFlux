package aicrediflux.token.rag.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import aicrediflux.token.rag.domain.RagDocument;

@Mapper
public interface RagDocumentMapper extends BaseMapper<RagDocument> {
    @Select("SELECT * FROM mr_rag_document WHERE status <> 'DELETED' ORDER BY id DESC LIMIT #{limit}")
    List<RagDocument> selectRecent(@Param("limit") int limit);

    @Select("SELECT * FROM mr_rag_document WHERE doc_no=#{docNo} AND status <> 'DELETED' LIMIT 1")
    RagDocument selectByDocNo(@Param("docNo") String docNo);

    @Select("SELECT * FROM mr_rag_document WHERE space=#{space} AND title=#{title} AND content_hash=#{contentHash} AND status <> 'DELETED' ORDER BY id DESC LIMIT 1")
    RagDocument selectSameContent(@Param("space") String space, @Param("title") String title, @Param("contentHash") String contentHash);

    @Select("SELECT COALESCE(MAX(version), 0) FROM mr_rag_document WHERE space=#{space} AND title=#{title}")
    int selectMaxVersion(@Param("space") String space, @Param("title") String title);

    @Update("UPDATE mr_rag_document SET status=#{status}, chunk_count=#{chunkCount}, error_message=#{errorMessage}, indexed_at=#{indexedAt}, update_time=#{now} WHERE doc_no=#{docNo}")
    int markStatus(@Param("docNo") String docNo, @Param("status") String status, @Param("chunkCount") int chunkCount,
                   @Param("errorMessage") String errorMessage, @Param("indexedAt") Instant indexedAt, @Param("now") Instant now);

    @Update("UPDATE mr_rag_document SET status='DELETED', update_time=#{now} WHERE doc_no=#{docNo} AND status <> 'DELETED'")
    int markDeleted(@Param("docNo") String docNo, @Param("now") Instant now);
}
