package cn.sdh.backend.job;

public class PlatformJobCancelledException extends RuntimeException {
    public PlatformJobCancelledException() {
        super("任务已取消");
    }
}
