package ca.gc.aafc.reportlabel.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import ca.gc.aafc.dina.DinaBaseApiAutoConfiguration;
import ca.gc.aafc.dina.service.JaversDataService;

@Configuration
@ComponentScan(basePackageClasses = DinaBaseApiAutoConfiguration.class)
@ImportAutoConfiguration(DinaBaseApiAutoConfiguration.class)
@MapperScan(basePackageClasses = JaversDataService.class)
public class MainConfiguration {

}
