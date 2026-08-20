package com.shipflow.sf;

import com.shipflow.sf.client.SfApiRequest;
import com.shipflow.sf.client.SfSignUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SfSignUtilTest {
    @Test
    void followsOfficialMd5Base64UrlEncodedFormula() {
        SfSignUtil util = new SfSignUtil();
        String digest = util.digest(new SfApiRequest("partner", "request", "service", 1_700_000_000L,
                null, "{\"a\":1}"), "secret");
        assertThat(digest).isEqualTo("iAzwHN95ycSHw+JqzZaszg==");
    }

    @Test
    void preservesTimestampAsDecimalEpochSeconds() {
        SfSignUtil util = new SfSignUtil();
        assertThat(util.digest("{}", "0", "secret"))
                .isEqualTo("NkvqjL8jn2uUwEQYwa5tmw==");
    }
}
