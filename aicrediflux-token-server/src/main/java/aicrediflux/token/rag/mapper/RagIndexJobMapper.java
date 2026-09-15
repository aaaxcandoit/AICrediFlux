package aicrediflux.token.rag.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import aicrediflux.token.rag.domain.RagIndexJob;

@Mapper
public interface RagIndexJobMapper extends BaseMapper<RagIndexJob> {
    @Update("UPDATE mr_rag_index_job SET status=#{status}, total_chunks=#{totalChunks}, success_chunks=#{successChunks}, failed_reason=#{failedReason}, update_time=#{now} WHERE job_no=#{jobNo}")
    int finish(@Param("jobNo") String jobNo, @Param("status") String status, @Param("totalChunks") int totalChunks,
               @Param("successChunks") int successChunks, @Param("failedReason") String failedReason, @Param("now") Instant now);
}
