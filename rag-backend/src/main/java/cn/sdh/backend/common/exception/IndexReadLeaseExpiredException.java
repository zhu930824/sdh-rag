package cn.sdh.backend.common.exception;

/** Retrying retrieval must open a new published view; never continue with expired evidence. */
public final class IndexReadLeaseExpiredException extends BusinessException {
    public IndexReadLeaseExpiredException() { super(503,"检索读租约已过期，请重新提交问题"); }
}
