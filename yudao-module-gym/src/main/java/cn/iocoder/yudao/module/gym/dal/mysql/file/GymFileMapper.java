package cn.iocoder.yudao.module.gym.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * gym 文件 Mapper
 *
 * 提示：本表继承 TenantBaseDO，MyBatis Plus 的租户插件会自动追加 {@code tenant_id = ?}，
 * 因此查询无需（也不应该）手写 tenant_id 条件。
 *
 * 扩展示例（后续做「文件列表 / 分页」接口时可直接照抄）：
 * <pre>{@code
 * default PageResult<GymFileDO> selectPage(GymFilePageReqVO reqVO) {
 *     return selectPage(reqVO, new LambdaQueryWrapperX<GymFileDO>()
 *             .eqIfPresent(GymFileDO::getStoreId, reqVO.getStoreId())
 *             .eqIfPresent(GymFileDO::getCategory, reqVO.getCategory())
 *             .likeIfPresent(GymFileDO::getName, reqVO.getName())
 *             .betweenIfPresent(GymFileDO::getCreateTime, reqVO.getCreateTime())
 *             .orderByDesc(GymFileDO::getId));
 * }
 * }</pre>
 *
 * @author JSS
 */
@Mapper
public interface GymFileMapper extends BaseMapperX<GymFileDO> {
}
