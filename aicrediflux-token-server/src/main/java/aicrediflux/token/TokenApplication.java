package aicrediflux.token;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;

import com.alicp.jetcache.anno.config.EnableMethodCache;

/**
 * Token 网关
 *
 * @author aicrediflux
 */
@MapperScan(value = {"aicrediflux.token.mapper", "aicrediflux.token.flashsale.mapper", "aicrediflux.token.integration.credit",
        "aicrediflux.token.agent.mapper", "aicrediflux.token.rag.mapper"}, annotationClass = Mapper.class)
@EnableAsync
@EnableScheduling
@EnableMethodCache(basePackages = "aicrediflux.token")
@SpringBootApplication
public class TokenApplication {

    public static void main(String[] args) {
        SpringApplication.run(TokenApplication.class, args);
    }

}

