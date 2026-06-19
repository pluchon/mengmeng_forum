package org.example.forumdemo.entity.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminForumNoticeSaveRequest {

    @NotNull
    @Min(0)
    @Max(4)
    private Byte noticeKind;

    @NotNull
    @Min(0)
    private Long categoryScope;

    @NotBlank
    @Size(max = 64)
    private String templateId;

    @NotBlank
    @Size(max = 64)
    private String sidebarKey;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 500)
    private String subtitle;

    /** 正文 Markdown */
    @NotBlank
    private String contentMarkdown;

    /** 模板扩展 JSON（封面、亮点等）；可空，服务端按模板补默认 */
    private String bodyJson;

    @NotNull
    @Min(0)
    @Max(1)
    private Byte pinTop;

    @NotNull
    @Min(0)
    @Max(1)
    private Byte publishState;
}
