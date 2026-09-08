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

    @ExcelProperty("相关文档标注")
    @ColumnWidth(30)
    private String relevanceJudgments;

    @ExcelProperty("检索到的文档IDs")
    @ColumnWidth(30)
    private String retrievedDocumentIds;

    @ExcelProperty("Recall@K")
    @ColumnWidth(12)
    private String recallAtK;

    @ExcelProperty("Precision@K")
    @ColumnWidth(12)
    private String precisionAtK;

    @ExcelProperty("MRR")
    @ColumnWidth(10)
    private String reciprocalRank;

    @ExcelProperty("NDCG@K")
    @ColumnWidth(12)
    private String ndcgAtK;

    @ExcelProperty("AP")
    @ColumnWidth(10)
    private String averagePrecision;

    @ExcelProperty("实际答案")
    @ColumnWidth(50)
    private String actualAnswer;

    @ExcelProperty("忠实度")
    @ColumnWidth(10)
    private String faithfulness;

    @ExcelProperty("答案相关性")
    @ColumnWidth(12)
    private String answerRelevancy;

    @ExcelProperty("完整性")
    @ColumnWidth(10)
    private String completeness;

    @ExcelProperty("流畅性")
    @ColumnWidth(10)
    private String fluency;

    @ExcelProperty("总体分")
    @ColumnWidth(10)
    private String overallScore;

    @ExcelProperty("评审理由")
    @ColumnWidth(50)
    private String judgeReason;

    @ExcelProperty("失败归因")
    @ColumnWidth(18)
    private String failureCategory;

    @ExcelProperty("改进建议")
    @ColumnWidth(50)
    private String failureSuggestion;

    @ExcelProperty("场景")
    @ColumnWidth(16)
    private String category;

    @ExcelProperty("难度")
    @ColumnWidth(10)
    private String difficulty;

    @ExcelProperty("业务线")
    @ColumnWidth(16)
    private String businessLine;

    @ExcelProperty("用户角色")
    @ColumnWidth(16)
    private String userRole;

    @ExcelProperty("人工总体分")
    @ColumnWidth(12)
    private String humanOverallScore;

    @ExcelProperty("人工复核说明")
    @ColumnWidth(40)
    private String humanReviewNote;
}
