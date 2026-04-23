package vip.mate.dashboard.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("mate_cron_job_run")
public class CronJobRunEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long cronJobId;
    private String conversationId;
    /** running / completed / failed */
    private String status;
    /** scheduled / manual */
    private String triggerType;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private Integer tokenUsage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
