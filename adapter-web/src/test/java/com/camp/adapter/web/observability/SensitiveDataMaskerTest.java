package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SensitiveDataMaskerTest {

    @ParameterizedTest
    @CsvSource({
        "'010-1234-5678', '010-****-5678'",
        "'01012345678', '010-****-5678'",
        "'010 1234 5678', '010-****-5678'",
        "'02-123-4567', '02-****-4567'",
        "'연락처 031.123.4567 입니다', '연락처 031-****-4567 입니다'",
    })
    @DisplayName("전화번호는 가운데 자리를 가린다")
    void masksPhoneNumbers(String raw, String expected) {
        assertThat(SensitiveDataMasker.mask(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "'uijin@example.com', 'u***@example.com'",
        "'first.last+tag@sub.example.co.kr', 'f***@sub.example.co.kr'",
        "'문의: help@camp.io 로', '문의: h***@camp.io 로'",
    })
    @DisplayName("이메일은 첫 글자와 도메인만 남긴다")
    void masksEmails(String raw, String expected) {
        assertThat(SensitiveDataMasker.mask(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("전화번호와 이메일이 한 줄에 같이 있어도 둘 다 가린다")
    void masksBothInOneLine() {
        String raw = "회원 uijin@example.com 010-1234-5678 조회";

        assertThat(SensitiveDataMasker.mask(raw)).isEqualTo("회원 u***@example.com 010-****-5678 조회");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "campaign 12345 created",
                "2026-08-02T09:15:30.123Z",
                "brand_id=1024 status=ACTIVE",
            })
    @DisplayName("개인정보가 아닌 숫자는 건드리지 않는다")
    void leavesOtherNumbersAlone(String raw) {
        assertThat(SensitiveDataMasker.mask(raw)).isEqualTo(raw);
    }

    @Test
    @DisplayName("숫자로만 된 계정명이 전화번호로 잘못 잡히지 않는다")
    void masksNumericEmailLocalPartAsEmail() {
        assertThat(SensitiveDataMasker.mask("01012345678@example.com")).isEqualTo("0***@example.com");
    }
}
