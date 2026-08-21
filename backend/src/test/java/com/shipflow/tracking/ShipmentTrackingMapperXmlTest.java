package com.shipflow.tracking;

import com.shipflow.tracking.mapper.ShipmentTrackingMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentTrackingMapperXmlTest {
    @Test
    void parsesUnifiedTimelineAndSimulationStatements() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/tracking/ShipmentTrackingMapper.xml")) {
            new XMLMapperBuilder(new InputStreamReader(input, StandardCharsets.UTF_8), configuration,
                    "mapper/tracking/ShipmentTrackingMapper.xml", configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement(ShipmentTrackingMapper.class.getName() + ".findOrderByReference")).isTrue();
        assertThat(configuration.hasStatement(ShipmentTrackingMapper.class.getName() + ".findTimeline")).isTrue();
        assertThat(configuration.hasStatement(ShipmentTrackingMapper.class.getName() + ".findSandboxOrders")).isTrue();
        assertThat(configuration.hasStatement(ShipmentTrackingMapper.class.getName() + ".insertSimulationEvent")).isTrue();
        assertThat(configuration.hasStatement(ShipmentTrackingMapper.class.getName() + ".insertSimulationTransportException")).isTrue();
        assertThat(configuration.hasStatement(ShipmentTrackingMapper.class.getName() + ".insertSimulationExceptionAudit")).isTrue();
        assertThat(configuration.hasStatement(ShipmentTrackingMapper.class.getName() + ".transitionOrder")).isTrue();
        assertThat(Files.readString(Path.of("src/main/resources/mapper/tracking/ShipmentTrackingMapper.xml")))
                .contains("o.store_id");
    }
}
