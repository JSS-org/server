package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

public final class GymMobilePublicVO {
    private GymMobilePublicVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicStorePage")
    public static class StorePage extends PageParam {
        @Size(max = 100)
        private String keyword;

        @DecimalMin("-180")
        @DecimalMax("180")
        @Digits(integer = 3, fraction = 7)
        private BigDecimal longitude;

        @DecimalMin("-90")
        @DecimalMax("90")
        @Digits(integer = 2, fraction = 7)
        private BigDecimal latitude;

        @AssertTrue(message = "经纬度必须同时提供")
        @JsonIgnore
        public boolean isCoordinatesPaired() {
            return (longitude == null) == (latitude == null);
        }
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicMedia")
    public record Media(String fileId, String type, String url, String expiresAt, String caption) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicStoreBrief")
    public record StoreBrief(
            String id, String name, Media cover, String address, Long distanceMeters) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicBusinessHours")
    public record BusinessHours(String opens, String closes) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicStoreDetail")
    public record StoreDetail(
            String id,
            String name,
            Media cover,
            String address,
            Long distanceMeters,
            String description,
            BigDecimal areaSqm,
            List<String> features,
            BigDecimal longitude,
            BigDecimal latitude,
            BusinessHours businessHours,
            List<Media> media,
            String servicePhone,
            String serviceWechat) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicOccupancy")
    public record Occupancy(
            Integer currentCount, long todayEntryCount, String updatedAt, String dataStatus) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicFeature")
    public record Feature(String key, boolean enabled, String disabledReason) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicNotice")
    public record Notice(String id, String title, String content, String publishedAt) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicHome")
    public record Home(
            StoreDetail store, Occupancy occupancy, List<Feature> features, List<Notice> notices) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicAppDownload")
    public record AppDownload(boolean enabled, String url, String benefitText) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicFranchiseModel")
    public record FranchiseModel(String name, Media preview, String modelUrl) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicFranchiseInfo")
    public record FranchiseInfo(
            boolean configured, String introduction, String phone, List<FranchiseModel> models) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "GymMobilePublicClientSettings")
    public record ClientSettings(
            Media entryCover, AppDownload appDownload, FranchiseInfo franchise) {}
}
