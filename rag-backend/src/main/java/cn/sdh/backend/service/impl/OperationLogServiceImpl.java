package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.OperationLog;
import cn.sdh.backend.mapper.OperationLogMapper;
import cn.sdh.backend.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public IPage<OperationLog> getPage(Integer page, Integer pageSize, String type, String username, Integer status, String startTime, String endTime) {
        Page<OperationLog> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(type)) {
            wrapper.eq(OperationLog::getType, type);
        }
        if (StringUtils.hasText(username)) {
            wrapper.like(OperationLog::getUsername, username);
        }
        if (status != null) {
            wrapper.eq(OperationLog::getStatus, status);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge(OperationLog::getCreateTime, LocalDateTime.parse(startTime, FORMATTER));
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le(OperationLog::getCreateTime, LocalDateTime.parse(endTime, FORMATTER));
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        
        return page(pageParam, wrapper);
    }
}