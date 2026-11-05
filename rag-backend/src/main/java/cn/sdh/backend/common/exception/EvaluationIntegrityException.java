package cn.sdh.backend.common.exception;

/** An incomplete or drifting execution must not produce a comparable evaluation score. */
public final class EvaluationIntegrityException extends BusinessException {
    public EvaluationIntegrityException() { super(409,"评测输入或模型调用未完整确认，本批结果不能作为有效基线"); }
}
