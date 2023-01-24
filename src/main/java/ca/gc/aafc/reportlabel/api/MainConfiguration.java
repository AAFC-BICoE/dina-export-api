package ca.gc.aafc.reportlabel.api;

import ca.gc.aafc.dina.security.GroupAuthorizationService;
import io.crnk.core.queryspec.mapper.DefaultQuerySpecUrlMapper;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Locale;

@Configuration
@ComponentScan(basePackageClasses = GroupAuthorizationService.class)
// we can't use DinaBaseApiAutoConfiguration since it's too coupled with database related classes
@DependsOn({"querySpecUrlMapper"})
public class MainConfiguration implements WebMvcConfigurer {

  @Inject
  public void initQuerySpecUrlMapper(DefaultQuerySpecUrlMapper mapper) {
    mapper.setAllowCommaSeparatedValue(false);
  }

  @Bean
  public LocaleResolver localeResolver() {
    SessionLocaleResolver slr = new SessionLocaleResolver();
    slr.setDefaultLocale(Locale.ENGLISH);
    return slr;
  }

  @Bean
  public LocaleChangeInterceptor localeChangeInterceptor() {
    LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
    lci.setParamName("lang");
    return lci;
  }

  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this.localeChangeInterceptor());
  }

  @Bean
  @Named("validationMessageSource")
  public MessageSource baseValidationMessageSource() {
    ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
    messageSource.setAlwaysUseMessageFormat(true);
    messageSource.setBasename("classpath:base-validation-messages");
    messageSource.setDefaultEncoding("UTF-8");
    return messageSource;
  }

  @Bean
  public MessageSource messageSource() {
    ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
    messageSource.setAlwaysUseMessageFormat(true);
    messageSource.setBasename("classpath:validation-messages");
    messageSource.setDefaultEncoding("UTF-8");
    return messageSource;
  }

  @Bean
  public LocalValidatorFactoryBean getValidator() {
    LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
    bean.setValidationMessageSource(this.messageSource());
    return bean;
  }
}