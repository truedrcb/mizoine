package com.gratchev.mizoine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonControllerTest {
    @Test
    void testIssueAttachments() {
        final CommonController cut = new CommonController();
        assertThat(cut.issueAttachments("a", "b", "c", "d"))
                .isEqualTo("forward:/attachments/.mizoine/a/b/c/d");
        assertThat(cut.issueAttachments("/s/", "123^DD", "c", "/../../../.xxx"))
                .isEqualTo("forward:/attachments/.mizoine/%2Fs%2F/123%5EDD/c/%2F..%2F..%2F..%2F.xxx");
    }
}
