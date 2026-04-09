package cn.sdh.backend.controller;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.entity.Announcement;
import cn.sdh.backend.service.AnnouncementService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping("/list")
    public Result<IPage<Announcement>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status) {
        return Result.success(announcementService.getPage(page, pageSize, type, status));
    }

    @GetMapping("/active")
    public Result<List<Announcement>> activeList() {
        return Result.success(announcementService.getActiveAnnouncements());
    }

//    @GetMapping("/{id}")
//    public Result<Map<String, Object>> getDetail(@PathVariable Long id) {
//        Announcement announcement = announcementService.getById(id);
//        if (announcement == null) {
//            return Result.notFound("公告不存在");
//        }
//
//        Map<String, Object> result = new HashMap<>();
//        result.put("announcement", announcement);
//        result.put("readCount", announcementService.getReadCount(id));
//
//        Long userId = UserContext.getCurrentUserId();
//        if (userId != null) {
//            result.put("isRead", announcementService.hasRead(id, userId));
//        }
//
//        return Result.success(result);
//    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody Announcement announcement) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized();
        }
        
        announcement.setPublisherId(userId);
        announcement.setCreateTime(LocalDateTime.now());
        announcementService.save(announcement);
        return Result.success();
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Announcement announcement) {
        Announcement existing = announcementService.getById(id);
        if (existing == null) {
            return Result.notFound("公告不存在");
        }

        announcement.setId(id);
        announcement.setUpdateTime(LocalDateTime.now());
        announcementService.updateById(announcement);
        return Result.success();
    }

    @PostMapping("/publish/{id}")
    public Result<Void> publish(@PathVariable Long id) {
        Announcement announcement = announcementService.getById(id);
        if (announcement == null) {
            return Result.notFound("公告不存在");
        }

        Long userId = UserContext.getCurrentUserId();
        announcementService.publish(id, userId);
        return Result.success();
    }

    @PostMapping("/offline/{id}")
    public Result<Void> offline(@PathVariable Long id) {
        announcementService.offline(id);
        return Result.success();
    }

    @PostMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.removeById(id);
        return Result.success();
    }

//    @PostMapping("/{id}/read")
//    public Result<Void> markAsRead(@PathVariable Long id) {
//        Long userId = UserContext.getCurrentUserId();
//        if (userId == null) {
//            return Result.unauthorized();
//        }
//
//        announcementService.markAsRead(id, userId);
//        return Result.success();
//    }
}
