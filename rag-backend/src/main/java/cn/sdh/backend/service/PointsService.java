package cn.sdh.backend.service;

import cn.sdh.backend.entity.PointsRecord;
import cn.sdh.backend.entity.PointsGoods;
import cn.sdh.backend.entity.PointsExchange;
import cn.sdh.backend.entity.UserProfile;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface PointsService extends IService<PointsRecord> {

    UserProfile getOrCreateProfile(Long userId);

    IPage<PointsRecord> getPointsRecords(Long userId, Integer page, Integer pageSize);

    IPage<PointsGoods> getAvailableGoods(Integer page, Integer pageSize);

    IPage<PointsExchange> getUserExchanges(Long userId, Integer page, Integer pageSize);

    void addPoints(Long userId, int points, String type, String description, String relatedType, Long relatedId);

    void exchangeGoods(Long userId, Long goodsId);

    int getPointsBalance(Long userId);
}