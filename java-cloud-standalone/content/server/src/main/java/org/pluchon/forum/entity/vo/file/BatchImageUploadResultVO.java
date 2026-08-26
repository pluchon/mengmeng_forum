package org.pluchon.forum.entity.vo.file;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 批量上传图片结果：部分成功
@Data
public class BatchImageUploadResultVO {

    private List<SuccessItem> success = new ArrayList<>();

    private List<FailedItem> failed = new ArrayList<>();

    @Data
    public static class SuccessItem {
        private int index;
        private String url;
    }

    @Data
    public static class FailedItem {
        private int index;
        private String reason;
    }
}
