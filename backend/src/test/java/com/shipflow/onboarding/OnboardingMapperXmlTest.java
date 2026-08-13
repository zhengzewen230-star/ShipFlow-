package com.shipflow.onboarding;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;
class OnboardingMapperXmlTest { @Test void mapperUsesNoStarAndParses() throws Exception { String xml=Files.readString(Path.of("src/main/resources/mapper/onboarding/OnboardingMapper.xml"));assertThat(xml).contains("status='PENDING' AND version=#{version}","token_hash=#{tokenHash}","tenant_id=#{tenantId}").doesNotContain("SELECT *");var c=new org.apache.ibatis.session.Configuration();try(var input=Files.newInputStream(Path.of("src/main/resources/mapper/onboarding/OnboardingMapper.xml"))){new org.apache.ibatis.builder.xml.XMLMapperBuilder(input,c,"onboarding",c.getSqlFragments()).parse();}assertThat(c.hasStatement("com.shipflow.onboarding.mapper.OnboardingMapper.activateUser")).isTrue();} }
