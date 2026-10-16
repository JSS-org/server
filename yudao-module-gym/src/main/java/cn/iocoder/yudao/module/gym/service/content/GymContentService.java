package cn.iocoder.yudao.module.gym.service.content;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gym.controller.admin.content.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.content.*;
import cn.iocoder.yudao.module.gym.dal.mysql.content.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCourseSessionMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.net.URI;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GymContentService {
    @Resource private GymClientSettingMapper gymClientSettingMapper;
    @Resource private GymQrCodeMapper gymQrCodeMapper;
    @Resource private GymFileMapper gymFileMapper;
    @Resource private GymCourseSessionMapper gymCourseSessionMapper;
    @Resource private GymStoreMapper gymStoreMapper;
    @Resource private GymStoreService stores;
    @Resource private GymDataScope scope;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private void owner(Long store) {
        if (store == null) scope.requireHeadquarters();
        else stores.requireStore(store, false);
    }

    private void platform(String platform) {
        if (!Set.of("miniapp", "app").contains(platform)) throw exception(CLIENT_CONTENT_INVALID);
    }

    private GymClientSettingDO setting(String platform, Long store, boolean lock) {
        var q =
                new LambdaQueryWrapper<GymClientSettingDO>()
                        .eq(GymClientSettingDO::getScopeKey, store == null ? 0L : store)
                        .eq(GymClientSettingDO::getPlatform, platform);
        if (lock) q.last("FOR UPDATE");
        return gymClientSettingMapper.selectOne(q);
    }

    public Map<String, Object> settings(String platform, Long store) {
        platform(platform);
        owner(store);
        return settingsResponse(setting(platform, store, false), platform, store);
    }

    private void file(Long id, Long store, String kind) {
        var file = gymFileMapper.selectById(id);
        if (file == null
                || store != null && !store.equals(file.getStoreId())
                || !kind.equals(file.getCategory())
                || file.getMimeType() == null
                || !("image".equals(kind) && file.getMimeType().startsWith("image/")
                        || "video".equals(kind) && "video/mp4".equals(file.getMimeType())))
            throw exception(CLIENT_CONTENT_FILE_INVALID);
    }

    private Long fileId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw exception(CLIENT_CONTENT_FILE_INVALID);
        }
    }

    private boolean webUrl(String value) {
        try {
            var uri = URI.create(value);
            return Set.of("http", "https")
                            .contains(
                                    uri.getScheme() == null
                                            ? ""
                                            : uri.getScheme().toLowerCase(Locale.ROOT))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String styles(String input) {
        var result = new ArrayList<String>();
        for (String declaration : input.split(";")) {
            var parts = declaration.split(":", 2);
            if (parts.length != 2) continue;
            String name = parts[0].trim().toLowerCase(Locale.ROOT), value = parts[1].trim();
            boolean safe =
                    switch (name) {
                        case "text-align" -> value.matches("left|right|center|justify");
                        case "font-weight" -> value.matches("normal|bold|[1-9]00");
                        case "font-style" -> value.matches("normal|italic");
                        case "color", "background-color" ->
                                value.matches(
                                        "#[0-9A-Fa-f]{3,8}|[A-Za-z]{1,20}|rgba?\\([0-9.,% ]+\\)");
                        case "font-size" ->
                                value.matches("[0-9]{1,2}(\\.[0-9]{1,2})?(px|pt|rem|em|%)");
                        default -> false;
                    };
            if (safe) result.add(name + ":" + value);
        }
        return String.join(";", result);
    }

    private String html(String value, Long store) {
        Document doc = Jsoup.parseBodyFragment(value == null ? "" : value);
        doc.outputSettings().prettyPrint(false);
        for (var image : doc.select("img")) {
            String src = image.attr("src");
            if (!src.matches("gym-file:[1-9][0-9]{0,18}"))
                throw exception(CLIENT_CONTENT_FILE_INVALID);
            file(fileId(src.substring(9)), store, "image");
        }
        for (var link : doc.select("a[href]")) {
            String href = link.attr("href");
            if (href.matches("/gym/file-preview/[1-9][0-9]{0,18}")) {
                Long id = fileId(href.substring("/gym/file-preview/".length()));
                var media = gymFileMapper.selectById(id);
                if (media == null || !Set.of("image", "video").contains(media.getCategory()))
                    throw exception(CLIENT_CONTENT_FILE_INVALID);
                file(id, store, media.getCategory());
                link.attr("href", "gym-file-link:" + id);
            } else if (!webUrl(href)) link.removeAttr("href");
        }
        for (var element : doc.select("[style]")) {
            String safe = styles(element.attr("style"));
            if (safe.isEmpty()) element.removeAttr("style");
            else element.attr("style", safe);
        }
        var safe =
                Safelist.relaxed()
                        .addTags("h1", "h2", "h3", "h4", "h5", "h6", "span", "s", "u", "hr")
                        .addAttributes(":all", "style")
                        .removeProtocols("img", "src", "http", "https")
                        .addProtocols("img", "src", "gym-file")
                        .addProtocols("a", "href", "gym-file-link")
                        .addEnforcedAttribute("a", "rel", "noopener noreferrer");
        String cleaned =
                Jsoup.clean(
                        doc.body().html(),
                        "",
                        safe,
                        new Document.OutputSettings().prettyPrint(false));
        var output = Jsoup.parseBodyFragment(cleaned);
        output.outputSettings().prettyPrint(false);
        for (var link : output.select("a[href^=gym-file-link:]"))
            link.attr(
                    "href",
                    "/gym/file-preview/" + link.attr("href").substring("gym-file-link:".length()));
        return output.body().html();
    }

    private void content(GymClientSettingsReqVO req) {
        if (req.getAppId() == null) req.setAppId("");
        if (req.getWelcomeText() == null) req.setWelcomeText("");
        if (req.getCustomerService() == null) req.setCustomerService("");
        if (req.getAppDownloadUrl() != null && req.getAppDownloadUrl().isBlank())
            req.setAppDownloadUrl(null);
        if (req.getAppDownloadUrl() != null
                && (!"app".equals(req.getPlatform()) || !webUrl(req.getAppDownloadUrl())))
            throw exception(CLIENT_CONTENT_INVALID);
        if (req.getCoverFileId() != null) file(req.getCoverFileId(), req.getStoreId(), "image");
        var order = new HashSet<Integer>();
        for (var block : req.getBlocks()) {
            if (!order.add(block.getSortOrder())) throw exception(CLIENT_CONTENT_INVALID);
            switch (block.getBlockType()) {
                case "banner", "video" -> {
                    if (block.getFileId() == null) throw exception(CLIENT_CONTENT_FILE_INVALID);
                    file(
                            block.getFileId(),
                            req.getStoreId(),
                            "banner".equals(block.getBlockType()) ? "image" : "video");
                    if (block.getLinkUrl() != null) throw exception(CLIENT_CONTENT_INVALID);
                }
                case "text" -> {
                    if (block.getBody() == null
                            || block.getBody().isBlank()
                            || block.getFileId() != null
                            || block.getLinkUrl() != null) throw exception(CLIENT_CONTENT_INVALID);
                }
                case "link" -> {
                    if (block.getFileId() != null
                            || block.getLinkUrl() == null
                            || !webUrl(block.getLinkUrl())) throw exception(CLIENT_CONTENT_INVALID);
                }
                default -> throw exception(CLIENT_CONTENT_INVALID);
            }
        }
        req.setBlocks(
                req.getBlocks().stream()
                        .sorted(Comparator.comparing(GymClientSettingsReqVO.Block::getSortOrder))
                        .toList());
        req.setContentHtml(html(req.getContentHtml(), req.getStoreId()));
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> save(GymClientSettingsReqVO req) {
        validate(req);
        owner(req.getStoreId());
        content(req);
        String json = JsonUtils.toJsonString(req);
        var row = setting(req.getPlatform(), req.getStoreId(), true);
        if (row == null) {
            row = new GymClientSettingDO();
            row.setStoreId(req.getStoreId());
            row.setScopeKey(req.getStoreId() == null ? 0L : req.getStoreId());
            row.setPlatform(req.getPlatform());
            row.setRevision(1L);
            row.setContent(json);
            try {
                gymClientSettingMapper.insert(row);
                return settingsResponse(row, req.getPlatform(), req.getStoreId());
            } catch (DuplicateKeyException e) {
                row = setting(req.getPlatform(), req.getStoreId(), true);
                if (row == null) throw e;
            }
        }
        if (!json.equals(row.getContent())) {
            row.setContent(json);
            row.setRevision(row.getRevision() + 1);
            gymClientSettingMapper.updateById(row);
        }
        return settingsResponse(row, req.getPlatform(), req.getStoreId());
    }

    private Map<String, Object> settingsResponse(
            GymClientSettingDO row, String platform, Long store) {
        Map<String, Object> value;
        if (row == null) {
            value = new LinkedHashMap<>();
            value.put("systemName", store == null ? "健身管理" : stores.get(store).getName());
            value.put("appId", "");
            value.put("welcomeText", "");
            value.put("customerService", "");
            value.put("coverFileId", null);
            value.put("appDownloadUrl", null);
            value.put("enabled", false);
            value.put("contentHtml", "");
            value.put("blocks", List.of());
        } else value = new LinkedHashMap<>(JsonUtils.parseObject(row.getContent(), Map.class));
        value.put("platform", platform);
        value.put("storeId", store == null ? null : store.toString());
        value.put("revision", row == null ? 0L : row.getRevision());
        if (value.get("coverFileId") != null)
            value.put("coverFileId", value.get("coverFileId").toString());
        if (value.get("blocks") instanceof List<?> blocks)
            for (Object entry : blocks)
                if (entry instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    var block = (Map<String, Object>) raw;
                    if (block.get("fileId") != null)
                        block.put("fileId", block.get("fileId").toString());
                }
        return value;
    }

    public PageResult<GymQrCodeRespVO> codes(GymQrCodePageReqVO req) {
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        var allowed = scope.readableStoreIds();
        if (allowed != null && allowed.isEmpty()) return new PageResult<>(List.of(), 0L);
        var range = GymDateRange.of(req);
        var q =
                new LambdaQueryWrapper<GymQrCodeDO>()
                        .eq(req.getStoreId() != null, GymQrCodeDO::getStoreId, req.getStoreId())
                        .in(allowed != null, GymQrCodeDO::getStoreId, allowed)
                        .eq(req.getPlatform() != null, GymQrCodeDO::getPlatform, req.getPlatform())
                        .eq(
                                req.getSceneType() != null,
                                GymQrCodeDO::getSceneType,
                                req.getSceneType())
                        .eq(req.getStatus() != null, GymQrCodeDO::getStatus, req.getStatus())
                        .ge(range.from() != null, GymQrCodeDO::getCreatedAt, range.from())
                        .lt(range.to() != null, GymQrCodeDO::getCreatedAt, range.to())
                        .orderByDesc(GymQrCodeDO::getId);
        var page = gymQrCodeMapper.selectPage(req, q);
        return new PageResult<>(
                page.getList().stream().map(this::codeResponse).toList(), page.getTotal());
    }

    public GymQrCodeRespVO code(Long id) {
        var row = gymQrCodeMapper.selectById(id);
        if (row == null) throw exception(QR_CODE_NOT_EXISTS);
        scope.check(row.getStoreId());
        return codeResponse(row);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public GymQrCodeRespVO createCode(GymQrCodeReqVO req) {
        validate(req);
        scope.check(req.getStoreId());
        var store = gymStoreMapper.selectForUpdate(req.getStoreId());
        if (store == null) throw exception(STORE_NOT_EXISTS);
        if ("store".equals(req.getSceneType()) && req.getSceneId() != null
                || "course".equals(req.getSceneType()) && req.getSceneId() == null)
            throw exception(QR_CODE_SCENE_INVALID);
        String failure =
                "miniapp".equals(req.getPlatform()) ? "小程序码渠道未配置，未生成二维码" : "App场景跳转服务未配置，未生成二维码";
        if (!"active".equals(store.getStatus())) failure = "门店已停用，未生成二维码";
        if ("course".equals(req.getSceneType())) {
            var session = gymCourseSessionMapper.selectById(req.getSceneId());
            if (session == null || !req.getStoreId().equals(session.getStoreId()))
                throw exception(QR_CODE_SCENE_INVALID);
            if (!"scheduled".equals(session.getStatus())
                    || !clock.now().isBefore(session.getEndsAt())) failure = "课程已结束或取消，未生成二维码";
        }
        Long key = req.getSceneId() == null ? 0L : req.getSceneId();
        var row =
                gymQrCodeMapper.selectOne(
                        new LambdaQueryWrapper<GymQrCodeDO>()
                                .eq(GymQrCodeDO::getStoreId, req.getStoreId())
                                .eq(GymQrCodeDO::getPlatform, req.getPlatform())
                                .eq(GymQrCodeDO::getSceneType, req.getSceneType())
                                .eq(GymQrCodeDO::getSceneKey, key));
        // TODO-INTEGRATION-CLIENT-CODE: provider credentials/application routes and a verified
        // image are required before status can become generated.
        if (row != null) {
            if (!failure.equals(row.getFailureReason())) {
                row.setFailureReason(failure);
                gymQrCodeMapper.updateById(row);
            }
            return codeResponse(row);
        }
        row = BeanUtils.toBean(req, GymQrCodeDO.class);
        row.setSceneKey(key);
        row.setCreatedAt(clock.now().truncatedTo(ChronoUnit.MICROS));
        row.setStatus("failed");
        row.setFailureReason(failure);
        gymQrCodeMapper.insert(row);
        return codeResponse(row);
    }

    private GymQrCodeRespVO codeResponse(GymQrCodeDO row) {
        var result = BeanUtils.toBean(row, GymQrCodeRespVO.class);
        result.setStoreName(stores.get(row.getStoreId()).getName());
        return result;
    }
}
