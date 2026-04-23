package vip.mate.llm.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mate_model_provider")
public class ModelProviderEntity {

    @TableId
    private String providerId;

    private String name;

    private String apiKeyPrefix;

    private String chatModel;

    private String apiKey;

    private String baseUrl;

    private String generateKwargs;

    private Boolean isCustom;

    private Boolean isLocal;

    private Boolean supportModelDiscovery;

    private Boolean supportConnectionCheck;

    private Boolean freezeUrl;

    private Boolean requireApiKey;

    private String authType;

    private String oauthAccessToken;

    private String oauthRefreshToken;

    private Long oauthExpiresAt;

    private String oauthAccountId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
