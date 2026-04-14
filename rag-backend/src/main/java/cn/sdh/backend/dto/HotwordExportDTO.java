package cn.sdh.backend.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.util.Map;

/**
 * 热点词导出 DTO
 */
@Data
public class HotwordExportDTO {

    @ExcelProperty("排名")
    @ColumnWidth(8)
    private Integer rank;

    @ExcelProperty("词汇")
    @ColumnWidth(20)
    private String word;

    @ExcelProperty("查询次数")
    @ColumnWidth(12)
    private Integer count;

    @ExcelProperty("占比(%)")
    @ColumnWidth(12)
    private Double percent;

    @ExcelProperty("趋势(%)")
    @ColumnWidth(12)
    private Integer trend;

    @ExcelProperty("是否新词")
    @ColumnWidth(10)
    private String isNew;

    public static HotwordExportDTO fromMap(Map<String, Object> map, int rank) {
        HotwordExportDTO dto = new HotwordExportDTO();
        dto.setRank(rank);
        dto.setWord((String) map.get("word"));

        // count
        Object countObj = map.get("count");
        dto.setCount(countObj instanceof Number ? ((Number) countObj).intValue() : 0);

        // percent
        Object percentObj = map.get("percent");
        dto.setPercent(percentObj instanceof Number ? ((Number) percentObj).doubleValue() : 0.0);

        // trend
        Object trendObj = map.get("trend");
        dto.setTrend(trendObj instanceof Number ? ((Number) trendObj).intValue() : 0);

        // isNew
        Object isNewObj = map.get("isNew");
        dto.setIsNew(Boolean.TRUE.equals(isNewObj) ? "是" : "否");

        return dto;
    }
}
