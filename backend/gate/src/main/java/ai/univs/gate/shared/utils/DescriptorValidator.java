package ai.univs.gate.shared.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.util.Base64;

/**
 * base64 특징점 문자열을 게이트 계층에서 검증한다 (UG-279).
 *
 * <p><b>길이는 정확히 {@value #DESCRIPTOR_BYTES} 바이트여야 한다.</b> 시스템이 다룰 수 있는 특징점
 * 길이는 이 하나뿐이다.
 * <ul>
 *   <li>match-server 의 세 매칭 경로가 모두 비교 바이트 수를 {@code 512} 로 하드코딩한다
 *       ({@code IdentifyService}, {@code VerifyByDescriptorUseCase}, {@code VerifyByFaceIdUseCase}
 *       → {@code vlmatch(probe, target, 512)}).</li>
 *   <li>oracle DDL 이 {@code DESCRIPTOR RAW(520)}, {@code DESCRIPTOR_BODY RAW(512)} 로 못 박혀 있다.</li>
 * </ul>
 *
 * <p>길이를 느슨하게 두면 안 되는 이유는 500 이 아니라 <b>갤러리 오염</b>이다. match-server 의
 * {@code RegisterService} 는 들어온 본문을 길이 검사 없이 {@code descriptor_body} 에 영구 저장하고,
 * postgres {@code bytea} 도 oracle {@code RAW(512)}(최댓값이라 짧은 값도 통과) 도 이를 막지 않는다.
 * 짧은 본문이 한 행이라도 저장되면 그 프로젝트의 <b>모든 정상 1:N 매칭</b>이 그 행에 대해
 * {@code vlmatch(정상probe, 짧은본문, 512)} 를 실행하게 되어, 해당 테넌트의 매칭이 통째로
 * 망가진다. 즉 한 번의 잘못된 등록이 이후 모든 요청에 영향을 준다.
 *
 * <p>UG-279 가 이 검증을 필수로 만든 이유가 여기에 있다. 이 티켓 전까지 클라이언트가 준 descriptor
 * 는 1:1 일회성 비교에만 쓰였고, 등록은 반드시 추출기를 통과해야 했다. descriptor 기반 등록
 * API 는 <b>클라이언트가 준 값이 갤러리에 영구 저장되는 최초의 경로</b>다.
 *
 * <p>base64 디코딩 가능 여부도 함께 본다. 비 base64 는 match-server 의
 * {@code DescriptorDetail.from} 에서 {@code Base64.getDecoder().decode} 가
 * {@code IllegalArgumentException} 을 던지고, match-server 의
 * {@code @ExceptionHandler(Exception.class)} 에 걸려 500 이 된다 — 클라이언트는 자기 입력이
 * 잘못됐다는 사실조차 알 수 없다. gate 와 match-server 는 둘 다
 * {@code Base64.getDecoder()}(Basic) 를 쓰므로 판정이 어긋나지 않는다.
 *
 * <p>특징점 <b>버전</b>은 검증하지 않는다. match-server 가 {@code NOT_SUPPORTED_VERSION} 으로
 * 이미 정상적인 비즈니스 오류를 반환하고, 지원 버전 목록을 두 서비스에 중복 정의하면 버전 추가 시
 * 게이트가 먼저 막는 사고가 난다. 길이가 고정된 뒤로는 버전 바이트가 무엇이든 갤러리를 오염시킬 수
 * 없으므로 게이트에서 볼 이유도 없다.
 */
public class DescriptorValidator implements ConstraintValidator<ValidDescriptor, String> {

    /** 8바이트 헤더 + 512바이트 본문. */
    public static final int DESCRIPTOR_BYTES = 520;

    /** 520바이트를 Basic base64 로 인코딩한 정확한 길이 (패딩 포함). */
    public static final int ENCODED_LENGTH = 4 * ((DESCRIPTOR_BYTES + 2) / 3);

    @Override
    public boolean isValid(String descriptor, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(descriptor)) {
            return false;
        }

        // 디코딩 전에 문자열 길이로 먼저 끊는다. 거대한 입력을 디코딩해 메모리에 올리지 않기 위함.
        if (descriptor.length() != ENCODED_LENGTH) {
            return false;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(descriptor);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return decoded.length == DESCRIPTOR_BYTES;
    }
}
