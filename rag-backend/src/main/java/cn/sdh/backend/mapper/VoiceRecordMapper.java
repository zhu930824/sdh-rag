package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.VoiceRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VoiceRecordMapper extends BaseMapper<VoiceRecord> {

    @Select("SELECT * FROM voice_record WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<VoiceRecord> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM voice_record WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<VoiceRecord> selectBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT COUNT(*) FROM voice_record WHERE user_id = #{userId} AND status = 1")
    int countSuccessByUserId(@Param("userId") Long userId);
}
