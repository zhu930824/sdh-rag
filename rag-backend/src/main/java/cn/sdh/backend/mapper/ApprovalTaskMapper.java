package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.ApprovalTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {

    @Select("SELECT * FROM approval_task WHERE applicant_id = #{userId} ORDER BY create_time DESC")
    List<ApprovalTask> selectByApplicantId(@Param("userId") Long userId);

    @Select("SELECT * FROM approval_task WHERE status = 0 ORDER BY create_time DESC")
    List<ApprovalTask> selectPending();
}