package cn.iocoder.yudao.module.gym.service.file;

import cn.iocoder.yudao.module.gym.controller.admin.file.vo.GymFileUploadReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;

import jakarta.validation.Valid;

/**
 * gym 文件 Service 接口
 *
 * 能力：
 * 1. 上传：魔数校验内容类型 + 按类别限制大小 + 门店数据范围校验
 * 2. 元数据：按编号查询，含门店越权校验
 * 3. 内容：鉴权下载（不暴露 infra 的公开 url）
 *
 * @author JSS
 */
public interface GymFileService {

    /**
     * 上传文件
     *
     * @param uploadReqVO 上传信息（含文件内容）
     * @return 文件记录
     */
    GymFileDO uploadFile(@Valid GymFileUploadReqVO uploadReqVO);

    /**
     * 获得文件元数据（含门店越权校验）
     *
     * @param id 文件编号
     * @return 文件记录
     */
    GymFileDO getFile(Long id);

    /**
     * 获得文件内容（含门店越权校验）
     *
     * @param id 文件编号
     * @return 文件内容 + 下载所需元数据
     */
    GymFileContentDTO getFileContent(Long id);
}
