package com.camp.domain.archdemo;

import com.camp.application.shared.UseCase;

/** domain 패키지에 있으면서 application 을 참조한다. 컴파일은 되지만 의존성 규칙 위반이다. */
public class ArchViolationDemo {

    public Class<?> leak() {
        return UseCase.class;
    }
}
