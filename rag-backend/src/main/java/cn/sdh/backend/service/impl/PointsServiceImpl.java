package cn.sdh.backend.service.impl;

import cn.sdh.backend.entity.PointsRecord;
import cn.sdh.backend.entity.PointsGoods;
import cn.sdh.backend.entity.PointsExchange;
import cn.sdh.backend.entity.UserProfile;
import cn.sdh.backend.mapper.PointsRecordMapper;
import cn.sdh.backend.mapper.PointsGoodsMapper;
import cn.sdh.backend.mapper.PointsExchangeMapper;
import cn.sdh.backend.mapper.UserProfileMapper;
import cn.sdh.backend.service.PointsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PointsServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements PointsService {

    private final PointsRecordMapper pointsRecordMapper;
    private final PointsGoodsMapper pointsGoodsMapper;
    private final PointsExchangeMapper pointsExchangeMapper;
    private final UserProfileMapper userProfileMapper;

    @Override
    public UserProfile getOrCreateProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setPointsBalance(0);
            profile.setTotalPoints(0);
            profile.setLevel(1);
            profile.setExperience(0);
            profile.setCreateTime(LocalDateTime.now());
            profile.setUpdateTime(LocalDateTime.now());
            userProfileMapper.insert(profile);
        }
        return profile;
    }

    @Override
    public IPage<PointsRecord> getPointsRecords(Long userId, Integer page, Integer pageSize) {
        Page<PointsRecord> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<PointsRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsRecord::getUserId, userId)
               .orderByDesc(PointsRecord::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public IPage<PointsGoods> getAvailableGoods(Integer page, Integer pageSize) {
        Page<PointsGoods> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<PointsGoods> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsGoods::getStatus, 1)
               .gt(PointsGoods::getStock, 0)
               .orderByAsc(PointsGoods::getPoints);
        return pointsGoodsMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public IPage<PointsExchange> getUserExchanges(Long userId, Integer page, Integer pageSize) {
        Page<PointsExchange> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<PointsExchange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsExchange::getUserId, userId)
               .orderByDesc(PointsExchange::getCreateTime);
        return pointsExchangeMapper.selectPage(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPoints(Long userId, int points, String type, String description, String relatedType, Long relatedId) {
        UserProfile profile = getOrCreateProfile(userId);
        
        int newBalance = profile.getPointsBalance() + points;
        profile.setPointsBalance(newBalance);
        if (points > 0) {
            profile.setTotalPoints(profile.getTotalPoints() + points);
        }
        profile.setUpdateTime(LocalDateTime.now());
        userProfileMapper.updateById(profile);

        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setPoints(points);
        record.setBalance(newBalance);
        record.setType(type);
        record.setDescription(description);
        record.setRelatedType(relatedType);
        record.setRelatedId(relatedId);
        record.setCreateTime(LocalDateTime.now());
        pointsRecordMapper.insert(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exchangeGoods(Long userId, Long goodsId) {
        PointsGoods goods = pointsGoodsMapper.selectById(goodsId);
        if (goods == null || goods.getStatus() != 1 || goods.getStock() <= 0) {
            throw new RuntimeException("商品不可兑换");
        }

        UserProfile profile = getOrCreateProfile(userId);
        if (profile.getPointsBalance() < goods.getPoints()) {
            throw new RuntimeException("积分不足");
        }

        PointsExchange exchange = new PointsExchange();
        exchange.setUserId(userId);
        exchange.setGoodsId(goodsId);
        exchange.setPoints(goods.getPoints());
        exchange.setStatus(0);
        exchange.setCreateTime(LocalDateTime.now());
        pointsExchangeMapper.insert(exchange);

        addPoints(userId, -goods.getPoints(), "redeem", "兑换: " + goods.getName(), "goods", goodsId);

        goods.setStock(goods.getStock() - 1);
        pointsGoodsMapper.updateById(goods);
    }

    @Override
    public int getPointsBalance(Long userId) {
        UserProfile profile = getOrCreateProfile(userId);
        return profile.getPointsBalance();
    }
}