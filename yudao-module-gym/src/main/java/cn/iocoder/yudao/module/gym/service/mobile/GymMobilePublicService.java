package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.gym.controller.admin.content.vo.GymClientSettingsReqVO;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePublicVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.content.GymClientSettingDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCheckinDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.*;
import cn.iocoder.yudao.module.gym.dal.mysql.content.GymClientSettingMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCheckinMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.*;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;

import com.baomidou.mybatisplus.core.conditions.query.*;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GymMobilePublicService {
    @Resource private GymStoreMapper stores;
    @Resource private GymStoreMediaMapper media;
    @Resource private GymFileMapper files;
    @Resource private GymCheckinMapper checkins;
    @Resource private GymClientSettingMapper settings;
    @Resource private FileApi storage;
    @Resource private GymBusinessClock clock;
    @Resource private WebProperties web;
    @Resource private Validator validator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<StoreBrief> stores(StorePage req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        TenantContextHolder.getRequiredTenantId();
        var q = new QueryWrapper<GymStoreDO>().eq("status", "active");
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String keyword = req.getKeyword().strip();
            q.and(w -> w.like("name", keyword).or().like("address", keyword));
        }
        if (req.getLongitude() != null) {
            // Validated BigDecimals, never client SQL. Order before pagination, missing positions
            // last.
            String lat = req.getLatitude().toPlainString(),
                    lon = req.getLongitude().toPlainString();
            q.orderByAsc(
                    "CASE WHEN longitude IS NULL OR latitude IS NULL THEN 1 ELSE 0 END",
                    "ACOS(LEAST(1,GREATEST(-1,SIN(RADIANS("
                            + lat
                            + "))*SIN(RADIANS(latitude))+COS(RADIANS("
                            + lat
                            + "))*COS(RADIANS(latitude))*COS(RADIANS(longitude-"
                            + "("
                            + lon
                            + "))))))");
        }
        var page = stores.selectPage(req, q.orderByAsc("id"));
        var gallery = gallery(page.getList());
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                s ->
                                        new StoreBrief(
                                                s.getId().toString(),
                                                s.getName(),
                                                gallery.get(s.getId()).cover(),
                                                s.getAddress(),
                                                distance(s, req)))
                        .toList(),
                page.getTotal());
    }

    public GymStoreDO requirePublicStore(Long id) {
        TenantContextHolder.getRequiredTenantId();
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        var row = stores.selectById(id);
        if (row == null || !"active".equals(row.getStatus())) throw exception(STORE_NOT_EXISTS);
        return row;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StoreDetail store(Long id) {
        return detail(requirePublicStore(id));
    }

    private StoreDetail detail(GymStoreDO row) {
        var items = gallery(List.of(row)).get(row.getId());
        return new StoreDetail(
                row.getId().toString(),
                row.getName(),
                items.cover(),
                row.getAddress(),
                null,
                row.getDescription(),
                row.getAreaSqm(),
                JsonUtils.parseArray(row.getFeatures(), String.class),
                row.getLongitude(),
                row.getLatitude(),
                new BusinessHours(row.getOpens(), row.getCloses()),
                items.items(),
                row.getServicePhone(),
                row.getServiceWechat());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Home home(Long id) {
        var row = requirePublicStore(id);
        var config = effective("miniapp", id);
        Instant now = clock.now();
        var notices =
                config == null || config.getNotices() == null
                        ? List.<Notice>of()
                        : config.getNotices().stream()
                                .filter(
                                        n ->
                                                !n.getPublishedAt().isAfter(now)
                                                        && (n.getExpiresAt() == null
                                                                || n.getExpiresAt()
                                                                        .isAfter(clock.now())))
                                .sorted(
                                        Comparator.comparing(
                                                        GymClientSettingsReqVO.Notice
                                                                ::getPublishedAt)
                                                .reversed()
                                                .thenComparing(
                                                        GymClientSettingsReqVO.Notice::getId))
                                .map(
                                        n ->
                                                new Notice(
                                                        n.getId().toString(),
                                                        n.getTitle(),
                                                        n.getContent(),
                                                        time(n.getPublishedAt())))
                                .toList();
        return new Home(
                detail(row),
                occupancyValue(id),
                List.of(
                        new Feature("view_cards", true, null),
                        new Feature("book_courses", true, null),
                        new Feature("view_records", true, null),
                        new Feature("view_contracts", true, null),
                        new Feature("payment", false, "external_integration_deferred"),
                        new Feature("auto_renewal", false, "external_integration_deferred"),
                        new Feature("e_sign", false, "external_integration_deferred"),
                        new Feature("access_control", false, "external_integration_deferred"),
                        new Feature("camera", false, "external_integration_deferred")),
                notices);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Occupancy occupancy(Long id) {
        requirePublicStore(id);
        return occupancyValue(id);
    }

    private Occupancy occupancyValue(Long id) {
        Instant now = clock.now();
        Instant start =
                now.atZone(GymDateRange.ZONE)
                        .toLocalDate()
                        .atStartOfDay(GymDateRange.ZONE)
                        .toInstant();
        long count =
                checkins.selectCount(
                        new LambdaQueryWrapper<GymCheckinDO>()
                                .eq(GymCheckinDO::getStoreId, id)
                                .eq(GymCheckinDO::getStatus, "succeeded")
                                .ge(GymCheckinDO::getEnteredAt, start)
                                .le(GymCheckinDO::getEnteredAt, now));
        var last =
                checkins.selectList(
                        new LambdaQueryWrapper<GymCheckinDO>()
                                .eq(GymCheckinDO::getStoreId, id)
                                .eq(GymCheckinDO::getStatus, "succeeded")
                                .le(GymCheckinDO::getEnteredAt, now)
                                .orderByDesc(GymCheckinDO::getEnteredAt)
                                .last("LIMIT 1"));
        // No verified gateway health/full ingress+egress source yet; never infer occupancy from
        // entries.
        return new Occupancy(
                null,
                count,
                last.isEmpty() ? null : time(last.getFirst().getEnteredAt()),
                "unavailable");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ClientSettings settings(Long store) {
        TenantContextHolder.getRequiredTenantId();
        if (store != null) requirePublicStore(store);
        var miniapp = effective("miniapp", store);
        var app = effective("app", store);
        boolean download = app != null && safeUrl(app.getAppDownloadUrl());
        Media cover =
                miniapp == null
                        ? null
                        : configuredMedia(miniapp.getCoverFileId(), miniapp.getStoreId(), null);
        var franchise = miniapp == null ? null : miniapp.getFranchise();
        var models =
                franchise == null
                        ? List.<FranchiseModel>of()
                        : franchise.getModels().stream()
                                .map(
                                        m ->
                                                new FranchiseModel(
                                                        m.getName(),
                                                        configuredMedia(
                                                                m.getPreviewFileId(),
                                                                miniapp.getStoreId(),
                                                                m.getName()),
                                                        safeUrl(m.getModelUrl())
                                                                ? m.getModelUrl()
                                                                : null))
                                .toList();
        boolean configured =
                franchise != null
                        && models.size() == 3
                        && models.stream().allMatch(m -> m.preview() != null);
        return new ClientSettings(
                cover,
                new AppDownload(
                        download,
                        download ? app.getAppDownloadUrl() : null,
                        download ? app.getAppBenefitText() : null),
                configured
                        ? new FranchiseInfo(
                                true, franchise.getIntroduction(), franchise.getPhone(), models)
                        : new FranchiseInfo(false, "", "", List.of()));
    }

    private GymClientSettingsReqVO effective(String platform, Long store) {
        GymClientSettingDO row = store == null ? null : setting(platform, store);
        if (row == null) row = setting(platform, 0L);
        return published(row);
    }

    private GymClientSettingDO setting(String platform, Long scope) {
        return settings.selectOne(
                new LambdaQueryWrapper<GymClientSettingDO>()
                        .eq(GymClientSettingDO::getPlatform, platform)
                        .eq(GymClientSettingDO::getScopeKey, scope));
    }

    private GymClientSettingsReqVO published(GymClientSettingDO row) {
        if (row == null) return null;
        try {
            var value = JsonUtils.parseObject(row.getContent(), GymClientSettingsReqVO.class);
            if (value == null
                    || !Boolean.TRUE.equals(value.getEnabled())
                    || !validator.validate(value).isEmpty()) return null;
            // Scope comes from the persisted owner, not nested JSON supplied by a client.
            value.setStoreId(row.getStoreId());
            return value;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record Gallery(Media cover, List<Media> items) {}

    private Map<Long, Gallery> gallery(List<GymStoreDO> rows) {
        if (rows.isEmpty()) return Map.of();
        var links =
                media.selectList(
                        new LambdaQueryWrapper<GymStoreMediaDO>()
                                .in(
                                        GymStoreMediaDO::getStoreId,
                                        rows.stream().map(GymStoreDO::getId).toList())
                                .orderByAsc(GymStoreMediaDO::getSortOrder, GymStoreMediaDO::getId));
        Map<Long, GymFileDO> fileMap =
                links.isEmpty()
                        ? Map.of()
                        : files
                                .selectByIds(
                                        links.stream().map(GymStoreMediaDO::getFileId).toList())
                                .stream()
                                .collect(Collectors.toMap(GymFileDO::getId, f -> f));
        var result = new HashMap<Long, Gallery>();
        for (var row : rows) {
            var items = new ArrayList<Media>();
            Media cover = null;
            for (var link : links) {
                if (!row.getId().equals(link.getStoreId())) continue;
                var file = fileMap.get(link.getFileId());
                if (!publicFile(file) || !row.getId().equals(file.getStoreId())) continue;
                var item = mediaValue(file, link.getCaption());
                items.add(item);
                if (cover == null
                        && Boolean.TRUE.equals(link.getIsCover())
                        && "image".equals(item.type())) cover = item;
            }
            result.put(row.getId(), new Gallery(cover, List.copyOf(items)));
        }
        return result;
    }

    private Media configuredMedia(Long id, Long owner, String caption) {
        if (id == null) return null;
        var row = files.selectById(id);
        return publicFile(row)
                        && "image".equals(row.getCategory())
                        && (owner == null || owner.equals(row.getStoreId()))
                ? mediaValue(row, caption)
                : null;
    }

    private Media mediaValue(GymFileDO row, String caption) {
        return new Media(
                row.getId().toString(),
                row.getCategory(),
                web.getAppApi().getPrefix()
                        + "/gym/public-media/"
                        + row.getId()
                        + "/content?tenantId="
                        + TenantContextHolder.getRequiredTenantId(),
                null,
                caption);
    }

    private static boolean publicFile(GymFileDO row) {
        return row != null
                && ("image".equals(row.getCategory())
                                && Set.of("image/png", "image/jpeg", "image/gif", "image/webp")
                                        .contains(Objects.toString(row.getMimeType(), ""))
                        || "video".equals(row.getCategory())
                                && Set.of("video/mp4", "video/quicktime")
                                        .contains(Objects.toString(row.getMimeType(), "")));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymFileContentDTO mediaContent(Long id) {
        TenantContextHolder.getRequiredTenantId();
        var file = files.selectById(id);
        if (!publicFile(file) || !publishedReference(file)) throw exception(FILE_NOT_EXISTS);
        byte[] bytes = storage.getFileContentByUrl(file.getUrl());
        if (bytes == null || bytes.length == 0) throw exception(FILE_CONTENT_NOT_EXISTS);
        var detected = GymFileTypeUtils.detect(bytes);
        if (detected == null || !detected.getMimeType().equals(file.getMimeType()))
            throw exception(FILE_NOT_EXISTS);
        return new GymFileContentDTO(file.getName(), file.getMimeType(), bytes);
    }

    private boolean publishedReference(GymFileDO file) {
        if (media.selectCount(
                        new LambdaQueryWrapper<GymStoreMediaDO>()
                                .eq(GymStoreMediaDO::getFileId, file.getId())
                                .eq(GymStoreMediaDO::getStoreId, file.getStoreId())
                                .exists(
                                        "SELECT 1 FROM gym_store s WHERE s.id=gym_store_media.store_id AND s.tenant_id={0} AND s.deleted=0 AND s.status='active'",
                                        TenantContextHolder.getRequiredTenantId()))
                > 0) return true;
        if (!"image".equals(file.getCategory())) return false;
        var candidates =
                settings.selectList(
                        new LambdaQueryWrapper<GymClientSettingDO>()
                                .eq(GymClientSettingDO::getPlatform, "miniapp")
                                .and(
                                        q ->
                                                q.isNull(GymClientSettingDO::getStoreId)
                                                        .or()
                                                        .exists(
                                                                "SELECT 1 FROM gym_store s WHERE s.id=gym_client_setting.store_id AND s.tenant_id={0} AND s.deleted=0 AND s.status='active'",
                                                                TenantContextHolder
                                                                        .getRequiredTenantId())));
        for (var row : candidates) {
            var config = published(row);
            if (config == null
                    || row.getStoreId() != null && !row.getStoreId().equals(file.getStoreId()))
                continue;
            if (file.getId().equals(config.getCoverFileId())) return true;
            if (config.getFranchise() != null
                    && config.getFranchise().getModels().stream()
                            .allMatch(
                                    m ->
                                            configuredMedia(
                                                            m.getPreviewFileId(),
                                                            row.getStoreId(),
                                                            null)
                                                    != null)
                    && config.getFranchise().getModels().stream()
                            .anyMatch(m -> file.getId().equals(m.getPreviewFileId()))) return true;
        }
        return false;
    }

    private static boolean safeUrl(String value) {
        if (value == null) return false;
        try {
            var uri = java.net.URI.create(value);
            return Set.of("https", "http")
                            .contains(
                                    Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Long distance(GymStoreDO store, StorePage req) {
        if (req.getLongitude() == null
                || store.getLongitude() == null
                || store.getLatitude() == null) return null;
        double a = Math.toRadians(req.getLatitude().doubleValue()),
                b = Math.toRadians(store.getLatitude().doubleValue());
        double x = Math.sin((b - a) / 2),
                y =
                        Math.sin(
                                Math.toRadians(
                                                store.getLongitude()
                                                        .subtract(req.getLongitude())
                                                        .doubleValue())
                                        / 2);
        double h = x * x + Math.cos(a) * Math.cos(b) * y * y;
        return Math.round(6371000 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, h)))));
    }

    private static String time(Instant value) {
        return value == null ? null : value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }
}
