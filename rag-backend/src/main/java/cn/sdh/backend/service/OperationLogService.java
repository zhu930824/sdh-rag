package cn.sdh.backend.service;

import cn.sdh.backend.entity.OperationLog;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface OperationLogService {

    IPage<OperationLog> getPage(Integer page, Integer pageSize, String type, String username, 
                                 Integer status, String startTime, String endTime);

    OperationLog getById(Long id);

    void save(OperationLog log);

    void asyncSaveLog(OperationLog log);
}