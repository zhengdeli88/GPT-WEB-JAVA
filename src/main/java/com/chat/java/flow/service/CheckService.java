package com.chat.java.flow.service;

import com.chat.java.base.B;
import com.chat.java.constant.CommonConst;
import com.chat.java.model.GptKey;
import com.chat.java.model.UseLog;
import com.chat.java.model.User;
import com.chat.java.model.res.UserInfoRes;
import com.chat.java.service.*;
import com.chat.java.utils.RedisUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.websocket.Session;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ClassName:service
 * Package:com.chat.java.flow
 * Description:
 *
 * @Author: ShenShiPeng
 * @Create: 2023/4/4 - 10:03
 * @Version: v1.0
 */
@Service("CheckService")
public class CheckService {

    @Resource
    IGptKeyService gptKeyService;
    @Resource
    IUserService userService;

    @Resource
    IRefuelingKitService refuelingKitService;

    @Resource
    IUseLogService useLogService;

    @Resource
    AsyncLogService asyncLogService;
    public B checkUser(String mainKey, Long userId, Session session) throws IOException {
        String redisToken  = RedisUtil.getCacheObject(CommonConst.REDIS_KEY_PREFIX_TOKEN + userId);
        if(StringUtils.isEmpty(redisToken)){
            session.getBasicRemote().sendText("请先登录");
            return B.buildGptErr("请先登录");
        }
        List<GptKey> gptKeyList = gptKeyService.lambdaQuery().eq(GptKey::getKey, mainKey).last("limit 1").list();
        if(null == gptKeyList || gptKeyList.size() == 0){
            session.getBasicRemote().sendText("Key 异常 请稍后重试");
            return B.buildGptErr("Key 异常 请稍后重试");
        }
        //查询当前用户信息
        UseLog useLog = new UseLog();
        useLog.setGptKey(mainKey);
        useLog.setUserId(userId);
        User user = userService.getById(userId);
        B<UserInfoRes> userInfo = userService.getType(userId);
        Integer type = userInfo.getData().getType();
        if(type != 2){
            if(type == 0){
                //判断剩余次数
                if(user.getRemainingTimes() < 1){
                    session.getBasicRemote().sendText("剩余次数不足请充值");
                    return B.buildGptErr("剩余次数不足请充值");
                }
                useLog.setUseType(1);
                user.setRemainingTimes(user.getRemainingTimes() - 1);
            }
            if(type == 1){
                //月卡用户 先查询是否有可用的加油包
                Long userKitId = refuelingKitService.getUserKitId();
                if(userKitId > 0){
                    useLog.setKitId(userKitId);
                    useLog.setUseType(3);
                }else {
                    //判断月卡是否到期
                    if(user.getExpirationTime().compareTo(LocalDateTime.now()) < 0){
                        //次数用户 查询用户次数
                        if(user.getRemainingTimes() < 1){
                            session.getBasicRemote().sendText("月卡过期或当日已超过最大访问次数");
                            return B.buildGptErr("月卡过期或当日已超过最大访问次数");
                        }
                        useLog.setUseType(1);
                        user.setRemainingTimes(user.getRemainingTimes() - 1);
                    }else {
                        //是否已达今日已达上线
                        Integer dayUseNumber = useLogService.getDayUseNumber();
                        if((dayUseNumber + 1) > user.getCardDayMaxNumber()){
                            //判断剩余次数
                            if(user.getRemainingTimes() < 1){
                                session.getBasicRemote().sendText("月卡过期或当日已超过最大访问次数");
                                return B.buildGptErr("月卡过期或当日已超过最大访问次数");
                            }
                            useLog.setUseType(1);
                            user.setRemainingTimes(user.getRemainingTimes() - 1);
                        }else {
                            useLog.setUseType(2);
                        }
                    }
                }
            }
        }
        GptKey gptKey = gptKeyList.get(0);
        gptKey.setUseNumber(gptKey.getUseNumber()+1);
        gptKey.setOperateTime(LocalDateTime.now());
        asyncLogService.saveKeyLog(gptKey,user);
        return B.buildGptData(useLog);
    }
}
