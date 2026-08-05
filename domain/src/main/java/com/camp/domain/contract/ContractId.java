package com.camp.domain.contract;

/** 계약 식별자. 원시 Long 을 도메인 API 에 노출하지 않아 ID 뒤바뀜이 컴파일 에러가 된다. */
public record ContractId(long value) {

    public ContractId {
        if (value <= 0) {
            throw new IllegalArgumentException("ContractId 는 양수여야 한다. 시도한 값: " + value);
        }
    }

    public static ContractId of(long value) {
        return new ContractId(value);
    }

    @Override
    public String toString() {
        return "ContractId(" + value + ")";
    }
}
