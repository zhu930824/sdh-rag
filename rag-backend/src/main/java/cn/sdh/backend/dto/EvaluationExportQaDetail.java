package cn.sdh.backend.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 评估报告导出 - QA详情Sheet
 */
@Data
public class EvaluationExportQaDetail {

    @ExcelProperty("序号")
    @ColumnWidth(8)
    private Integer index;

    @ExcelProperty("问题")
    @ColumnWidth(50)
    private String question;

    @ExcelProperty("期望答案")
    @ColumnWidth(40)
    private String expectedAnswer;

    @ExcelProperty("样本类型")
    @ColumnWidth(10)
    private String sampleType;

    @ExcelProperty("分块命中状态")
    @ColumnWidth(12)
    private String hitStatus;

    @ExcelProperty("分块命中排名")
    @ColumnWidth(12)
    private String hitRank;

    @ExcelProperty("文档命中状态")
    @ColumnWidth(12)
    private String docHitStatus;

    @ExcelProperty("文档命中排名")
    @ColumnWidth(12)
    private String docHitRank;

    @ExcelProperty("检索到的分块IDs")
    @ColumnWidth(30)
    private String retrievedChunkIds;
}
