package cn.sdh.backend.job;

import cn.sdh.backend.entity.PlatformJob;

public interface PlatformJobHandler {
    String jobType();

    String execute(PlatformJob job, PlatformJobExecutionContext context) throws Exception;
}
