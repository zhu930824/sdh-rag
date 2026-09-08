package cn.sdh.backend.demo;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 演示业务手工查证端点(登录态可用)。Agent 工具与页面共用同一服务层。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/demo-business")
public class DemoBusinessController {

    private final DemoBusinessService demoService;

    @GetMapping("/orders")
    public Result<Object> orders(@RequestParam String customer) {
        return Result.success(demoService.queryOrders(Map.of("customer", customer)));
    }

    @GetMapping("/inventory")
    public Result<Object> inventory(@RequestParam String product) {
        return Result.success(demoService.queryInventory(Map.of("product", product)));
    }

    @GetMapping("/tickets")
    public Result<Object> tickets(@RequestParam(required = false) String customer,
                                  @RequestParam(required = false) String ticketNo) {
        return Result.success(demoService.queryTickets(Map.of("customer", customer == null ? "" : customer,
                "ticketNo", ticketNo == null ? "" : ticketNo)));
    }

    @PostMapping("/tickets")
    public Result<Object> createTicket(@RequestBody Map<String, Object> request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        return Result.success("演示工单已创建", demoService.createServiceTicket(request, userId));
    }
}
