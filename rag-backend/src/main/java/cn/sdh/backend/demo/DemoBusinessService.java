package cn.sdh.backend.demo;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.DemoTicket;
import cn.sdh.backend.mapper.DemoTicketMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内置演示业务(订单/库存内存样例 + 售后工单落 demo_ticket)。
 * 供 agent_tool 以 internal://demo/xxx 直调,也供手工 curl 查证闭环结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoBusinessService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DemoTicketMapper ticketMapper;
    private final AtomicInteger ticketSeq = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000, 9999));

    // ---------- 订单/库存(只读内存样例) ----------

    public Object queryOrders(Map<String, Object> args) {
        DemoSeedData.requireParams(args, "customer");
        String customer = DemoSeedData.text(args.get("customer"));
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> order : DemoSeedData.orders()) {
            if (order.get("customer").equals(customer)) {
                matches.add(order);
            }
        }
        return matches;
    }

    public Object queryInventory(Map<String, Object> args) {
        DemoSeedData.requireParams(args, "product");
        String product = DemoSeedData.text(args.get("product"));
        Object stock = DemoSeedData.inventory().get(product);
        if (stock == null) {
            throw new BusinessException(404, "商品不存在: " + product);
        }
        return stock;
    }

    // ---------- 售后工单(落库,审批后二次调用可查证) ----------

    public Object createServiceTicket(Map<String, Object> args, Long actor) {
        DemoSeedData.requireParams(args, "customer", "orderNo", "title");
        String customer = DemoSeedData.text(args.get("customer"));
        String orderNo = DemoSeedData.text(args.get("orderNo"));
        String title = DemoSeedData.text(args.get("title"));
        String detail = DemoSeedData.text(args.get("detail"));

        // 订单必须真实存在,防止模型虚构订单号
        boolean orderExists = DemoSeedData.orders().stream()
                .anyMatch(o -> orderNo.equals(o.get("orderNo")) && customer.equals(o.get("customer")));
        if (!orderExists) {
            throw new BusinessException(400, "订单不存在或不属于该客户,请先用订单查询工具核对订单号");
        }

        DemoTicket ticket = new DemoTicket();
        ticket.setTicketNo(nextTicketNo());
        ticket.setCustomer(customer);
        ticket.setOrderNo(orderNo);
        ticket.setTitle(title);
        ticket.setDetail(detail);
        ticket.setStatus(DemoTicket.STATUS_OPEN);
        ticket.setCreatedBy(actor);
        LocalDateTime now = LocalDateTime.now();
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);
        try {
            ticketMapper.insert(ticket);
        } catch (Exception e) {
            // 工单号撞唯一键时重试一次
            ticket.setTicketNo(nextTicketNo());
            ticketMapper.insert(ticket);
        }
        return ticketView(ticket);
    }

    public Object updateTicketStatus(Map<String, Object> args, Long actor) {
        DemoSeedData.requireParams(args, "ticketNo", "action");
        String ticketNo = DemoSeedData.text(args.get("ticketNo"));
        String action = DemoSeedData.text(args.get("action")).toUpperCase(Locale.ROOT);
        DemoTicket ticket = findTicket(ticketNo);

        String next;
        switch (action) {
            case "PROCESS" -> {
                if (!DemoTicket.STATUS_OPEN.equals(ticket.getStatus())
                        && !DemoTicket.STATUS_PROCESSING.equals(ticket.getStatus())) {
                    throw new BusinessException(400, "仅 OPEN/PROCESSING 状态的工单可处理");
                }
                next = DemoTicket.STATUS_PROCESSING;
            }
            case "RESOLVE" -> {
                if (!DemoTicket.STATUS_PROCESSING.equals(ticket.getStatus())) {
                    throw new BusinessException(400, "仅 PROCESSING 状态的工单可解决,请先 PROCESS");
                }
                next = DemoTicket.STATUS_RESOLVED;
            }
            case "CLOSE" -> {
                if (!DemoTicket.STATUS_RESOLVED.equals(ticket.getStatus())
                        && !DemoTicket.STATUS_CLOSED.equals(ticket.getStatus())) {
                    throw new BusinessException(400, "仅 RESOLVED 状态的工单可关闭");
                }
                next = DemoTicket.STATUS_CLOSED;
            }
            default -> throw new BusinessException(400, "action 仅支持 PROCESS/RESOLVE/CLOSE");
        }
        ticket.setStatus(next);
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return ticketView(ticket);
    }

    public Object queryTickets(Map<String, Object> args) {
        LambdaQueryWrapper<DemoTicket> wrapper = new LambdaQueryWrapper<>();
        String ticketNo = DemoSeedData.text(args.get("ticketNo"));
        String customer = DemoSeedData.text(args.get("customer"));
        if (ticketNo != null && !ticketNo.isBlank()) {
            wrapper.eq(DemoTicket::getTicketNo, ticketNo);
        } else if (customer != null && !customer.isBlank()) {
            wrapper.eq(DemoTicket::getCustomer, customer);
        } else {
            throw new BusinessException(400, "请提供 ticketNo 或 customer");
        }
        wrapper.orderByDesc(DemoTicket::getCreateTime).last("LIMIT 50");
        List<DemoTicket> tickets = ticketMapper.selectList(wrapper);
        return tickets.stream().map(this::ticketView).toList();
    }

    private DemoTicket findTicket(String ticketNo) {
        DemoTicket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<DemoTicket>()
                .eq(DemoTicket::getTicketNo, ticketNo));
        if (ticket == null) {
            throw new BusinessException(404, "工单不存在: " + ticketNo);
        }
        return ticket;
    }

    private Map<String, Object> ticketView(DemoTicket ticket) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ticketNo", ticket.getTicketNo());
        view.put("customer", ticket.getCustomer());
        view.put("orderNo", ticket.getOrderNo());
        view.put("title", ticket.getTitle());
        view.put("detail", ticket.getDetail());
        view.put("status", ticket.getStatus());
        view.put("createTime", ticket.getCreateTime() == null ? null
                : ticket.getCreateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return view;
    }

    private String nextTicketNo() {
        String seq = String.format("%04d", ticketSeq.incrementAndGet() % 10000);
        return "ST" + LocalDateTime.now().format(DAY) + "-" + seq;
    }
}
