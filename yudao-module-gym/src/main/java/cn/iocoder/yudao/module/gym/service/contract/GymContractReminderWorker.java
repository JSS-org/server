package cn.iocoder.yudao.module.gym.service.contract;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.*;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.*;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessClock;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Internal tenant-scoped task. System actor 0 is an audit identity, never a privileged login. */
@Service
public class GymContractReminderWorker {
    @Resource private GymContractMapper contracts;
    @Resource private GymContractReminderMapper reminders;
    @Resource private GymContractEventMapper events;
    @Resource private GymMemberMapper members;
    @Resource private GymStoreMapper stores;
    @Resource private GymBusinessClock clock;

    public static String dailyKey(Instant at, String channel) {
        return "auto:" + at.atZone(GymDateRange.ZONE).toLocalDate() + ":" + channel;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean process(Long id, String channel) {
        if (!Set.of("app", "miniapp", "sms").contains(channel)) throw new IllegalArgumentException("Invalid reminder channel");
        var row = contracts.selectForUpdate(id);
        if (row == null || !Set.of("unsigned", "sent", "viewed").contains(row.getStatus()) || row.getSignedAt() != null || row.getCancelledAt() != null) return false;
        var store = stores.selectById(row.getStoreId());
        var member = members.selectById(row.getMemberId());
        if (store == null || !"active".equals(store.getStatus()) || member == null || !"active".equals(member.getStatus())) return false;
        var template = JsonUtils.parseObject(row.getTemplateSnapshot(), GymContractTemplateDO.class);
        Integer days = template == null ? null : template.getReminderDays();
        if (days == null || days < 0 || days > 365 || row.getDueAt() == null) return false;
        Instant now = clock.now().truncatedTo(ChronoUnit.MICROS);
        var start = row.getDueAt().atZone(GymDateRange.ZONE).toLocalDate().minusDays(days).atStartOfDay(GymDateRange.ZONE).toInstant();
        if (now.isBefore(start)) return false;
        String key = dailyKey(now, channel);
        if (reminders.selectCount(new LambdaQueryWrapper<GymContractReminderDO>().eq(GymContractReminderDO::getContractId, id)
                .eq(GymContractReminderDO::getRequestKey, key)) > 0) return false;
        var reminder = new GymContractReminderDO();
        reminder.setContractId(id); reminder.setChannel(channel); reminder.setRequestKey(key);
        reminder.setScheduledAt(now); reminder.setCompletedAt(now); reminder.setRequestedBy(0L);
        reminder.setCreator("0"); reminder.setUpdater("0");
        // TODO-INTEGRATION-NOTIFICATION: send only through a configured, verified member delivery adapter.
        reminder.setStatus("failed");
        reminder.setFailureReason(Map.of("sms", "短信", "miniapp", "小程序", "app", "App").get(channel) + "提醒渠道未配置，自动提醒未发送");
        reminders.insert(reminder);
        var event = new GymContractEventDO();
        event.setContractId(id); event.setType("automatic_reminder_failed"); event.setOccurredAt(now);
        event.setActorUserId(0L); event.setDetail(reminder.getFailureReason());
        event.setCreator("0"); event.setUpdater("0"); events.insert(event);
        return true;
    }
}
