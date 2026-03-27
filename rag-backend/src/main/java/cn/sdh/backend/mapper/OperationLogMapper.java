package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.OperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    IPage<OperationLog> selectPageByCondition(Page<OperationLog> page,
                                               @Param("type") String type,
                                               @Param("username") String username,
                                               @Param("status") Integer status,
                                               @Param("startTime") String startTime,
                                               @Param("endTime") String endTime);
}