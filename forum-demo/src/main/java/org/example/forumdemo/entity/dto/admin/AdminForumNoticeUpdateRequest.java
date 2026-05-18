package org.example.forumdemo.entity.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminForumNoticeUpdateRequest extends AdminForumNoticeSaveRequest {

    @NotNull
    private Long id;
}
