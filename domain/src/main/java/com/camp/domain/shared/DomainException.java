package com.camp.domain.shared;

/** 도메인 규칙 위반의 최상위 타입. 인프라 장애는 이 타입을 쓰지 않는다. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
