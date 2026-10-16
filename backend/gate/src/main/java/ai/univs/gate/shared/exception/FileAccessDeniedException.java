package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.enums.ErrorType;

/** 사진 URL 거절 (UG-386) — 다른 업무 오류(400)와 달리 403 으로 응답한다. */
public class FileAccessDeniedException extends BusinessException {

    public FileAccessDeniedException() {
        super(ErrorType.FILE_ACCESS_DENIED);
    }
}
