package vip.mate.wiki;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiki 知识库模块自动配置
 *
 * @author MateClaw Team
 */
@Configuration
@EnableConfigurationProperties(WikiProperties.class)
public class WikiAutoConfiguration {
}
