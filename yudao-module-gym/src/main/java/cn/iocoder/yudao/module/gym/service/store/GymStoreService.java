package cn.iocoder.yudao.module.gym.service.store;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.store.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.store.*;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

@Service
public class GymStoreService {
    @Resource private GymStoreMapper storeMapper;
    @Resource private GymStoreMediaMapper mediaMapper;
    @Resource private GymFileMapper fileMapper;
    @Resource private DeptService deptService;
    @Resource private GymDataScope scope;
    @Resource private Validator validator;

    public PageResult<GymStoreRespVO> page(GymPageReqVO req) {
        Set<Long> ids = scope.readableStoreIds();
        if (req.getStoreId() != null) scope.check(req.getStoreId());
        if (ids != null && ids.isEmpty()) return new PageResult<>(Collections.emptyList(), 0L);
        LambdaQueryWrapper<GymStoreDO> query = new LambdaQueryWrapper<>();
        if (ids != null) query.in(GymStoreDO::getId, ids);
        query.eq(req.getStoreId() != null, GymStoreDO::getId, req.getStoreId());
        query.eq(req.getStatus() != null, GymStoreDO::getStatus, req.getStatus());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(q -> q.like(GymStoreDO::getName, req.getKeyword()).or().like(GymStoreDO::getCode, req.getKeyword()));
        }
        query.orderByDesc(GymStoreDO::getId);
        PageResult<GymStoreDO> result = storeMapper.selectPage(req, query);
        return new PageResult<>(result.getList().stream().map(s -> response(s, false)).toList(), result.getTotal());
    }
    public GymStoreRespVO get(Long id) { return response(requireStore(id, false), true); }
    public GymStoreDO requireStore(Long id, boolean lock) {
        scope.check(id);
        GymStoreDO store = lock ? storeMapper.selectForUpdate(id) : storeMapper.selectById(id);
        if (store == null) throw exception(STORE_NOT_EXISTS);
        return store;
    }
    @Transactional(rollbackFor = Exception.class)
    public GymStoreRespVO create(GymStoreSaveReqVO req) {
        scope.requireHeadquarters(); // 新门店改变组织范围，仅全门店管理员可创建/绑定
        defaults(req);
        validate(req);
        if (storeMapper.selectOne(GymStoreDO::getCode, req.getCode()) != null) throw exception(STORE_CODE_DUPLICATE);
        Long deptId = req.getDeptId();
        if (deptId == null) {
            DeptSaveReqVO dept = new DeptSaveReqVO();
            dept.setName(req.getName());
            dept.setParentId(req.getParentDeptId() == null ? 0L : req.getParentDeptId());
            dept.setSort(0);
            dept.setStatus(0);
            deptId = deptService.createDept(dept);
        } else {
            DeptDO dept = deptService.getDept(deptId);
            if (dept == null || !Integer.valueOf(0).equals(dept.getStatus())) throw exception(STORE_DEPT_INVALID);
            if (storeMapper.selectById(deptId) != null) throw exception(STORE_CODE_DUPLICATE);
        }
        GymStoreDO store = toDO(req);
        store.setId(deptId);
        try { storeMapper.insert(store); } catch (DuplicateKeyException e) { throw exception(STORE_CODE_DUPLICATE); }
        return response(store, true);
    }
    @Transactional(rollbackFor = Exception.class)
    public GymStoreRespVO patch(Long id, GymStoreSaveReqVO patch) {
        GymStoreDO old = requireStore(id, true);
        if (patch.getDeptId() != null || patch.getParentDeptId() != null) throw exception(STORE_DEPT_INVALID);
        GymStoreSaveReqVO merged = BeanUtils.toBean(old, GymStoreSaveReqVO.class);
        merged.setFeatures(JsonUtils.parseArray(old.getFeatures(), String.class));
        GymStoreSaveReqVO.BusinessHours hours = new GymStoreSaveReqVO.BusinessHours();
        hours.setOpens(old.getOpens()); hours.setCloses(old.getCloses()); merged.setBusinessHours(hours);
        GymPatchUtils.merge(patch, merged);
        validate(merged);
        GymStoreDO duplicate = storeMapper.selectOne(GymStoreDO::getCode, merged.getCode());
        if (duplicate != null && !duplicate.getId().equals(id)) throw exception(STORE_CODE_DUPLICATE);
        GymStoreDO update = toDO(merged); update.setId(id);
        try { storeMapper.updateById(update); } catch (DuplicateKeyException e) { throw exception(STORE_CODE_DUPLICATE); }
        return response(update, true);
    }
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveMedia(Long id, GymStoreMediaReqVO req) {
        requireStore(id, true);
        Set<Long> seen = new HashSet<>(); int covers = 0;
        List<GymStoreMediaDO> rows = new ArrayList<>();
        for (GymStoreMediaReqVO.Item item : req.getItems()) {
            GymFileDO file = fileMapper.selectById(item.getFileId());
            if (file == null || !id.equals(file.getStoreId()) || !Set.of("image", "video").contains(file.getCategory())
                    || !seen.add(file.getId()) || (Boolean.TRUE.equals(item.getIsCover()) && !"image".equals(file.getCategory()))) {
                throw exception(STORE_MEDIA_INVALID);
            }
            if (Boolean.TRUE.equals(item.getIsCover())) covers++;
            GymStoreMediaDO row = BeanUtils.toBean(item, GymStoreMediaDO.class); row.setStoreId(id); rows.add(row);
        }
        if (covers > 1) throw exception(STORE_MEDIA_INVALID);
        mediaMapper.deleteRelations(id);
        rows.forEach(mediaMapper::insert);
        return true;
    }
    private void defaults(GymStoreSaveReqVO req) {
        if (req.getStatus() == null) req.setStatus("active");
        if (req.getFeatures() == null) req.setFeatures(Collections.emptyList());
        if (req.getDescription() == null) req.setDescription("");
    }
    private void validate(GymStoreSaveReqVO req) {
        var violations = validator.validate(req);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    }
    private GymStoreDO toDO(GymStoreSaveReqVO req) {
        GymStoreDO result = BeanUtils.toBean(req, GymStoreDO.class);
        result.setFeatures(JsonUtils.toJsonString(req.getFeatures()));
        result.setOpens(req.getBusinessHours().getOpens()); result.setCloses(req.getBusinessHours().getCloses());
        return result;
    }
    private GymStoreRespVO response(GymStoreDO store, boolean detail) {
        GymStoreRespVO result = BeanUtils.toBean(store, GymStoreRespVO.class);
        result.setId(store.getId().toString());
        result.setFeatures(JsonUtils.parseArray(store.getFeatures(), String.class));
        GymStoreSaveReqVO.BusinessHours hours = new GymStoreSaveReqVO.BusinessHours();
        hours.setOpens(store.getOpens()); hours.setCloses(store.getCloses()); result.setBusinessHours(hours);
        List<GymStoreRespVO.Media> media = new ArrayList<>();
        if (detail) {
            for (GymStoreMediaDO row : mediaMapper.selectList(new LambdaQueryWrapper<GymStoreMediaDO>()
                    .eq(GymStoreMediaDO::getStoreId, store.getId()).orderByAsc(GymStoreMediaDO::getSortOrder, GymStoreMediaDO::getId))) {
                GymFileDO file = fileMapper.selectById(row.getFileId());
                if (file == null) continue;
                GymStoreRespVO.Media item = BeanUtils.toBean(row, GymStoreRespVO.Media.class);
                item.setId(row.getId().toString()); item.setFileId(file.getId().toString());
                item.setCategory(file.getCategory()); item.setOriginalName(file.getName()); item.setName(file.getName());
                item.setMimeType(file.getMimeType()); item.setByteSize(file.getSize()); media.add(item);
            }
        }
        result.setMedia(media); return result;
    }
}
