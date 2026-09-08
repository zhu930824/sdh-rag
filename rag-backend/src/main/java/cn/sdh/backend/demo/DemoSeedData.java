package cn.sdh.backend.demo;

import cn.sdh.backend.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置演示业务数据种子(订单/库存内存样例,"张三"数据固定便于端到端脚本演示)。
 * 与售后工单(demo_ticket 落库)配合,验证"查订单→建工单→审批→落库可查证"闭环。
 */
public final class DemoSeedData {

    private DemoSeedData() {
    }

    public static final String CUSTOMER_ZHANG = "张三";

    /** 订单种子:字段固定 ASCII,便于 LLM 识别 */
    public static List<Map<String, Object>> orders() {
        List<Map<String, Object>> orders = new ArrayList<>();
        orders.add(order("SO20260701001", CUSTOMER_ZHANG, "智能手表", "1299.00", "已发货", "2026-07-01"));
        orders.add(order("SO20260615003", CUSTOMER_ZHANG, "无线耳机", "499.00", "已完成", "2026-06-15"));
        orders.add(order("SO20260720002", CUSTOMER_ZHANG, "机械键盘", "899.00", "配送中", "2026-07-20"));
        orders.add(order("SO20260718007", "李四", "笔记本电脑", "6999.00", "已完成", "2026-07-18"));
        orders.add(order("SO20260628001", "李四", "显示器", "1599.00", "已完成", "2026-06-28"));
        orders.add(order("SO20260725009", "王五", "智能手环", "249.00", "已发货", "2026-07-25"));
        orders.add(order("SO20260801005", "王五", "蓝牙音箱", "399.00", "待付款", "2026-08-01"));
        orders.add(order("SO20260730006", "赵六", "手机支架", "59.00", "已完成", "2026-07-30"));
        return orders;
    }

    public static Map<String, Object> inventory() {
        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("智能手表", stock("智能手表", "SKU-WATCH-01", 42));
        inventory.put("无线耳机", stock("无线耳机", "SKU-EARBUD-02", 128));
        inventory.put("机械键盘", stock("机械键盘", "SKU-KEYBOARD-03", 15));
        inventory.put("笔记本电脑", stock("笔记本电脑", "SKU-LAPTOP-04", 8));
        inventory.put("显示器", stock("显示器", "SKU-MONITOR-05", 23));
        inventory.put("智能手环", stock("智能手环", "SKU-BAND-06", 200));
        inventory.put("蓝牙音箱", stock("蓝牙音箱", "SKU-SPEAKER-07", 66));
        inventory.put("手机支架", stock("手机支架", "SKU-HOLDER-08", 320));
        return inventory;
    }

    private static Map<String, Object> order(String orderNo, String customer, String product,
                                             String amount, String status, String date) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderNo", orderNo);
        map.put("customer", customer);
        map.put("product", product);
        map.put("amount", amount);
        map.put("status", status);
        map.put("createDate", date);
        return map;
    }

    private static Map<String, Object> stock(String product, String sku, int quantity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("product", product);
        map.put("sku", sku);
        map.put("quantity", quantity);
        return map;
    }

    public static Map<String, Object> requireParams(Map<String, Object> args, String... keys) {
        for (String key : keys) {
            Object value = args == null ? null : args.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new BusinessException(400, "缺少必填参数: " + key);
            }
        }
        return args == null ? Map.of() : args;
    }

    public static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
