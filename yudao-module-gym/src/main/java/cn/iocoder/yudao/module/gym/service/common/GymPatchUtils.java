package cn.iocoder.yudao.module.gym.service.common;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import java.beans.PropertyDescriptor;
/** PATCH 的 null/省略表示保留；空字符串/空列表可显式清空可选值。 */
public final class GymPatchUtils {
    private GymPatchUtils() {}
    public static void merge(Object patch, Object target) {
        BeanWrapper source = new BeanWrapperImpl(patch);
        BeanWrapper dest = new BeanWrapperImpl(target);
        for (PropertyDescriptor pd : source.getPropertyDescriptors()) {
            String name = pd.getName();
            if (!dest.isWritableProperty(name)) continue;
            Object value = source.getPropertyValue(name);
            if (value != null) dest.setPropertyValue(name, value);
        }
    }
}
