package com.camp.adapter.web.observability;

import java.util.regex.Pattern;

/** 로그 출력 직전에 전화번호와 이메일을 가린다. 예외 메시지 안의 문자열은 거르지 못한다 (이슈 #34 예외 케이스). */
public final class SensitiveDataMasker {

    // 첫 글자와 도메인만 남긴다. 어느 계정인지 추적은 request_id 로 한다.
    private static final Pattern EMAIL =
            Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    // 0 으로 시작하는 9~11 자리. 앞뒤 숫자를 배제해 더 긴 숫자열의 일부를 잘못 잡지 않게 한다.
    private static final Pattern PHONE =
            Pattern.compile("(?<![0-9])(0[0-9]{1,2})[-. ]?([0-9]{3,4})[-. ]?([0-9]{4})(?![0-9])");

    private SensitiveDataMasker() {}

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        // 이메일을 먼저 가린다. 숫자로 된 계정명이 전화번호로 잡히는 것을 막는다.
        String masked = EMAIL.matcher(value).replaceAll("$1***@$2");
        return PHONE.matcher(masked).replaceAll("$1-****-$3");
    }
}
