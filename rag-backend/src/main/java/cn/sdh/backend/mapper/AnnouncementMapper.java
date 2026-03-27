package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.Announcement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {

    @Select("SELECT a.* FROM announcement a WHERE a.status = 1 AND (a.expire_time IS NULL OR a.expire_time > NOW()) ORDER BY a.is_top DESC, a.priority DESC, a.publish_time DESC")
    List<Announcement> selectActive();

    @Select("SELECT COUNT(*) FROM announcement_read WHERE announcement_id = #{announcementId}")
    int countReads(@Param("announcementId") Long announcementId);
}