package cn.iocoder.yudao.module.gym.dal.dataobject.file;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * gym 私有文件 DO
 *
 * 设计说明：
 * 1. 文件内容不落本表：交给 infra 的文件服务（{@code FileApi#createFile}）存储，
 *    好处是本地磁盘 / 数据库 / S3 / FTP 等存储配置一律复用 infra，无需自研。
 * 2. 本表只存「业务归属」：门店（system_dept）+ 类别 + 原始文件名 + 类型 / 大小 / 上传人，
 *    用于列表展示与越权校验（旧 Mock 的 fileAccess 门店 / 类别校验）。
 * 3. 继承 {@link TenantBaseDO}：多租户下文件自动按租户隔离；门店隔离靠 {@code storeId} + 数据范围校验。
 *
 * 表：gym_file，见 sql/gym/02-gym-file.sql
 *
 * @author JSS
 */
@TableName("gym_file")
@KeySequence("gym_file_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymFileDO extends TenantBaseDO {

    /**
     * 文件编号
     */
    @TableId
    private Long id;

    /**
     * 归属门店编号（system_dept.id；门店 = 部门）
     */
    private Long storeId;

    /**
     * 文件类别，见 {@link cn.iocoder.yudao.module.gym.enums.file.GymFileCategoryEnum}
     */
    private String category;

    /**
     * 原始文件名（含后缀，展示与下载用）
     */
    private String name;

    /**
     * infra 文件访问路径（{@code FileApi#createFile} 的返回值）
     *
     * 注意：该 URL 指向 infra 的 {@code @PermitAll} 下载接口，属于「存储凭证」，
     * 出于安全考虑**不返回给前端**；前端一律通过本模块的 {@code /gym/files/{id}/content} 鉴权下载。
     */
    private String url;

    /**
     * 实际内容类型（魔数嗅探结果）
     */
    private String mimeType;

    /**
     * 文件大小，单位：字节
     */
    private Long size;

    /**
     * 上传人编号（system_users.id）
     */
    private Long uploaderId;

}
