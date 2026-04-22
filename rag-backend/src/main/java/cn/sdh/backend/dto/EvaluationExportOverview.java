package cn.sdh.backend.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 评估报告导出 - 概览Sheet
 */
@Data
public class EvaluationExportOverview {

    @ExcelProperty("指标项")
    @ColumnWidth(20)
    private String metric;

    @ExcelProperty("数值")
    @ColumnWidth(25)
    private String value;

    public EvaluationExportOverview() {}

    public EvaluationExportOverview(String metric, String value) {
        this.metric = metric;
        this.value = value;
    }
}
