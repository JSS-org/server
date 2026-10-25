package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileConversationsVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GymMobileConversationsService {
    @Resource private GymMobileConversationMapper conversations;
    @Resource private GymMobileMessageMapper messages;
    @Resource private GymMobileIdentityService identities;
    @Resource private GymMobileWorkAccess work;
    @Resource private GymMemberService memberAccess;
    @Resource private GymMemberMapper members;
    @Resource private GymStoreMapper stores;
    @Resource private GymMemberStoreMapper memberships;
    @Resource private PermissionApi permissions;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    private record SendPayload(Long conversationId, String content) {}

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private void positive(Long id) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
    }

    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Long staff(boolean writing) {
        Long user = work.requireAdmin();
        if (!permissions.hasAnyPermissions(user, "gym:messages:read")
                || writing && !permissions.hasAnyPermissions(user, "gym:messages:send"))
            throw new AccessDeniedException("No permission to access member conversations");
        return user;
    }

    private void liveMembership(Long member, Long store, boolean writing) {
        var person = members.selectById(member);
        var location = stores.selectById(store);
        if (person == null
                || location == null
                || memberships.selectCount(
                                new LambdaQueryWrapper<GymMemberStoreDO>()
                                        .eq(GymMemberStoreDO::getMemberId, member)
                                        .eq(GymMemberStoreDO::getStoreId, store))
                        == 0) throw exception(MOBILE_CONVERSATION_UNAVAILABLE);
        if (writing
                && (!"active".equals(person.getStatus()) || !"active".equals(location.getStatus())))
            throw exception(MOBILE_CONVERSATION_UNAVAILABLE);
    }

    private void staffScope(Long member, Long store, boolean writing) {
        memberAccess.requireMember(member, false);
        memberAccess.checkMemberScope(member, store);
        liveMembership(member, store, writing);
    }

    private GymMobileConversationDO row(Long id, boolean lock) {
        positive(id);
        var q =
                new LambdaQueryWrapper<GymMobileConversationDO>()
                        .eq(GymMobileConversationDO::getId, id);
        if (lock) q.last("FOR UPDATE");
        var row = conversations.selectOne(q);
        if (row == null) throw exception(MOBILE_CONVERSATION_NOT_EXISTS);
        return row;
    }

    private GymMobileConversationDO staffConversation(Long id, boolean writing, boolean lock) {
        staff(writing);
        var row = row(id, lock);
        staffScope(row.getMemberId(), row.getStoreId(), writing);
        return row;
    }

    private GymMobileConversationDO memberConversation(Long id, boolean writing, boolean lock) {
        Long member = identities.requireMemberId();
        var row = row(id, lock);
        if (!member.equals(row.getMemberId())) throw exception(MOBILE_CONVERSATION_NOT_EXISTS);
        if (writing) liveMembership(member, row.getStoreId(), true);
        return row;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Created create(Create req, String key) {
        validate(req);
        Long user = staff(true);
        staffScope(req.getMemberId(), req.getStoreId(), true);
        return idempotency.execute(
                "conversation-create",
                key,
                req,
                Created.class,
                () -> {
                    mutex.lockStoreEntitlements();
                    staff(true);
                    staffScope(req.getMemberId(), req.getStoreId(), true);
                    var row =
                            conversations.selectOne(
                                    new LambdaQueryWrapper<GymMobileConversationDO>()
                                            .eq(
                                                    GymMobileConversationDO::getMemberId,
                                                    req.getMemberId())
                                            .eq(
                                                    GymMobileConversationDO::getStoreId,
                                                    req.getStoreId())
                                            .last("FOR UPDATE"));
                    if (row == null) {
                        row = new GymMobileConversationDO();
                        row.setStoreId(req.getStoreId());
                        row.setMemberId(req.getMemberId());
                        row.setCreatorUserId(user);
                        row.setInitiatedAt(now());
                        row.setMemberReadMessageId(0L);
                        row.setMemberUnreadCount(0);
                        conversations.insert(row);
                    }
                    return new Created(row.getId().toString());
                });
    }

    private Message response(GymMobileMessageDO row) {
        return new Message(
                row.getId().toString(),
                row.getConversationId().toString(),
                row.getSenderType() == 1 ? "member" : "employee",
                row.getContent(),
                row.getSentAt().atZone(GymDateRange.ZONE).toOffsetDateTime().toString());
    }

    private PageResult<Message> messagePage(Long id, Query req) {
        var result =
                messages.selectPage(
                        req,
                        new LambdaQueryWrapper<GymMobileMessageDO>()
                                .eq(GymMobileMessageDO::getConversationId, id)
                                .lt(
                                        req.getBeforeId() != null,
                                        GymMobileMessageDO::getId,
                                        req.getBeforeId())
                                .orderByDesc(GymMobileMessageDO::getId));
        return new PageResult<>(
                result.getList().stream().map(this::response).toList(), result.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Message> staffMessages(Long id, Query req) {
        validate(req);
        staffConversation(id, false, false);
        return messagePage(id, req);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Message> memberMessages(Long id, Query req) {
        validate(req);
        memberConversation(id, false, false);
        return messagePage(id, req);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Conversation> memberConversations(PageParam req) {
        validate(req);
        Long member = identities.requireMemberId();
        var page =
                conversations.selectPage(
                        req,
                        new LambdaQueryWrapper<GymMobileConversationDO>()
                                .eq(GymMobileConversationDO::getMemberId, member)
                                .orderByDesc(
                                        GymMobileConversationDO::getLastMessageId,
                                        GymMobileConversationDO::getId));
        var ids =
                page.getList().stream()
                        .map(GymMobileConversationDO::getLastMessageId)
                        .filter(Objects::nonNull)
                        .toList();
        Map<Long, GymMobileMessageDO> last =
                ids.isEmpty()
                        ? Map.of()
                        : messages.selectByIds(ids).stream()
                                .collect(
                                        Collectors.toMap(
                                                GymMobileMessageDO::getId, Function.identity()));
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                r -> {
                                    var msg =
                                            r.getLastMessageId() == null
                                                    ? null
                                                    : last.get(r.getLastMessageId());
                                    return new Conversation(
                                            r.getId().toString(),
                                            r.getStoreId().toString(),
                                            r.getMemberUnreadCount(),
                                            msg != null && r.getId().equals(msg.getConversationId())
                                                    ? response(msg)
                                                    : null);
                                })
                        .toList(),
                page.getTotal());
    }

    private Message send(Long id, Send req, String key, boolean staff) {
        validate(req);
        String content = req.getContent().trim();
        if (content.isBlank()) throw exception(MOBILE_REQUEST_INVALID);
        if (staff) staffConversation(id, true, false);
        else memberConversation(id, true, false);
        return idempotency.execute(
                "conversation-message",
                key,
                new SendPayload(id, content),
                Message.class,
                () -> {
                    var conversation =
                            staff
                                    ? staffConversation(id, true, true)
                                    : memberConversation(id, true, true);
                    Long user = staff ? work.requireAdmin() : identities.requireUser().getId();
                    var row = new GymMobileMessageDO();
                    row.setConversationId(id);
                    row.setSenderType(staff ? 2 : 1);
                    row.setSenderUserId(user);
                    row.setContent(content);
                    row.setSentAt(now());
                    messages.insert(row);
                    conversation.setLastMessageId(row.getId());
                    if (staff)
                        conversation.setMemberUnreadCount(
                                Math.addExact(conversation.getMemberUnreadCount(), 1));
                    conversations.updateById(conversation);
                    return response(row);
                });
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Message staffSend(Long id, Send req, String key) {
        return send(id, req, key, true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Message memberSend(Long id, Send req, String key) {
        return send(id, req, key, false);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReadState memberRead(Long id, Read req) {
        validate(req);
        var conversation = memberConversation(id, false, true);
        var message = messages.selectById(req.getLastReadMessageId());
        if (message == null || !id.equals(message.getConversationId()))
            throw exception(MOBILE_CONVERSATION_MESSAGE_INVALID);
        if (req.getLastReadMessageId() > conversation.getMemberReadMessageId()) {
            conversation.setMemberReadMessageId(req.getLastReadMessageId());
            long unread =
                    messages.selectCount(
                            new LambdaQueryWrapper<GymMobileMessageDO>()
                                    .eq(GymMobileMessageDO::getConversationId, id)
                                    .eq(GymMobileMessageDO::getSenderType, 2)
                                    .gt(GymMobileMessageDO::getId, req.getLastReadMessageId()));
            conversation.setMemberUnreadCount(Math.toIntExact(unread));
            conversations.updateById(conversation);
        }
        return new ReadState(
                conversation.getMemberReadMessageId().toString(),
                conversation.getMemberUnreadCount());
    }
}
