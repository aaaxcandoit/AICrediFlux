package aicrediflux.token.rag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import aicrediflux.token.rag.domain.RagChunk;

@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunk> {
    @Select("SELECT * FROM mr_rag_chunk WHERE doc_no=#{docNo} AND version=#{version} ORDER BY chunk_no")
    List<RagChunk> selectByDocumentVersion(@Param("docNo") String docNo, @Param("version") int version);

    @Delete("DELETE FROM mr_rag_chunk WHERE doc_no=#{docNo}")
    int deleteByDocNo(@Param("docNo") String docNo);
}
