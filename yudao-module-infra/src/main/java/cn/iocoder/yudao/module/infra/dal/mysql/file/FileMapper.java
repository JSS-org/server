package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;

import org.apache.ibatis.annotations.Mapper;

/**
 * 文件操作 Mapper
 *
 * @author 芋道源码
 */
@Mapper
public interface FileMapper extends BaseMapperX<FileDO> {

    default PageResult<FileDO> selectPage(FilePageReqVO reqVO) {
        return selectPage(
                reqVO,
                new LambdaQueryWrapperX<FileDO>()
                        .likeIfPresent(FileDO::getPath, reqVO.getPath())
                        .likeIfPresent(FileDO::getType, reqVO.getType())
                        .betweenIfPresent(FileDO::getCreateTime, reqVO.getCreateTime())
                        .orderByDesc(FileDO::getId));
    }

    default FileDO selectLatestByConfigIdAndPath(Long configId, String path) {
        return selectLastOne(
                new LambdaQueryWrapperX<FileDO>()
                        .eq(FileDO::getConfigId, configId)
                        .eq(FileDO::getPath, path)
                        .orderByAsc(FileDO::getId));
    }

    /**
     * 按访问地址查询文件（url 理论上唯一，取最新一条兜底）
     *
     * 用途：调用方只持有 {@link cn.iocoder.yudao.module.infra.api.file.FileApi#createFile} 返回的 url 时，
     * 反查 configId + path，进而读取文件内容。
     */
    default FileDO selectByUrl(String url) {
        return CollUtil.getFirst(
                selectList(
                        new LambdaQueryWrapperX<FileDO>()
                                .eq(FileDO::getUrl, url)
                                .orderByDesc(FileDO::getId)));
    }
}
