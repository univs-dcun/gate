package ai.univs.face.api.v2.dto;

/**
 * 호출자가 발급해 넘기는 얼굴 식별자의 형식 (UG-337).
 *
 * <p><b>왜 호출자가 발급하나.</b> gate 는 원격 등록이 성공한 뒤 자기 DB 에 쓴다. 그 쓰기가 실패하면
 * (커넥션을 못 얻거나 배포 중 프로세스가 끊기면) face 쪽에는 특징점이 있는데 gate 는 그 id 를 모른다
 * — id 가 원격 응답에만 있었기 때문이다. gate 가 id 를 먼저 만들어 자기 이력에 남기고 그 id 로
 * 등록하면, 실패해도 무엇을 되돌려야 하는지 안다(UG-338 의 정리 잡).
 *
 * <p><b>선택값이다.</b> 주지 않거나 빈 값이면 지금처럼 매처가 발급한다. 하위 호환이 깨지지 않는다.
 *
 * <p><b>UUID 형식만 받는다.</b> 매처의 {@code descriptor.face_id} 가 {@code VARCHAR(36)} 이라 그보다
 * 긴 값은 검증을 통과해도 DB 에서 터진다(v1 의 {@code faceId} 는 255자까지 받아 이 문제가 있다).
 *
 * <p><b>중복 id.</b> 같은 브랜치에 이미 있는 id 로 <b>차례로</b> 등록하면 매처가
 * {@code ALREADY_REGISTERED_DESCRIPTOR} 로 거절한다 — 덮어쓰지 않는다. 그러나 <b>동시에</b> 두 요청이
 * 오면 막지 못한다. 매처의 {@code descriptor} 에 {@code (branch_id, face_id)} 유니크 제약이 없고
 * {@code RegisterService} 가 조회한 뒤 저장하기 때문이다(반박 리뷰가 postgres 로 재현했다). 두 행이
 * 생기면 그 id 로 하는 삭제·검증이 영구히 실패한다.
 *
 * <p>gate 는 요청마다 새 UUID 를 발급하고 같은 id 로 다시 등록하지 않으므로 지금은 그 경로가 없다.
 * 같은 id 를 재사용하는 호출자가 생기기 전에 매처에 유니크 제약을 넣어야 한다.
 */
final class CallerIssuedFaceId {

    /** 빈 문자열(= 없음) 또는 UUID. */
    static final String PATTERN =
            "^$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    static final String DESCRIPTION =
            "호출자가 발급한 얼굴 식별자(UUID). 주지 않으면 서버가 발급한다. "
                    + "요청마다 새 값을 쓸 것 — 같은 값을 동시에 두 번 보내면 막지 못한다 (UG-337)";

    private CallerIssuedFaceId() {
    }
}
