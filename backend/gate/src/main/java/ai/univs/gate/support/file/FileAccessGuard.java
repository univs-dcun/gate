package ai.univs.gate.support.file;

import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.shared.exception.FileAccessDeniedException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 사진 API 가 내려주기 전에 확인한다 (UG-386).
 *
 * <ol>
 *   <li><b>서명 · 만료</b> — URL 이 우리 서버가 최근에 만든 것인지({@link FileUrlSigner}).
 *   <li><b>현재 동의</b> — 서명에 묶인 프로젝트의 개인정보 노출 동의가 <b>지금</b> 켜져 있는지. 예전에는 응답을 만들 때만
 *       동의를 봐서, 동의를 끈 뒤에도 이미 나간 URL 이 계속 열렸다. 삭제된 프로젝트도 거절한다.
 * </ol>
 *
 * <p>동의는 프로젝트마다 {@link #CONSENT_TTL} 동안 기억한다. 목록 한 쪽에 사진이 최대 40장이라 장마다 DB 를 두 번 읽으면
 * 커넥션 풀(gate 처리량의 상한, UG-359)을 쓸데없이 쓴다. 대가는 동의를 끈 뒤 최대 {@link #CONSENT_TTL} 동안 열릴 수 있다는 것.
 * 「꺼짐」은 기억하지 않는다 — 켠 직후 받은 URL 이 잠깐 거절되지 않게. 꺼진 프로젝트는 응답에 URL 을 싣지 않으므로
 * 이 조회는 끄기 직전에 나간 URL 에서만 일어나고, 서명을 먼저 보므로 임의의 projectId 로 유발할 수도 없다.
 *
 * <p><b>유예:</b> {@code file.signed-url.required=false} 이면 서명이 아예 없는 예전 URL 도 받는다(고객 공지 기간용).
 * 서명이 붙어 있는데 틀렸으면 이 값과 상관없이 거절한다. 유예 중에는 서명된 URL 에서 서명 쿼리만 지워도 기한·동의 확인 없이
 * 열리므로 보호 효과가 없다 — 공지 기간에만 짧게 쓴다.
 */
@Component
public class FileAccessGuard {

    static final Duration CONSENT_TTL = Duration.ofSeconds(5);
    private static final long MAX_PROJECTS = 10_000;

    private final FileUrlSigner signer;
    private final ProjectRepository projectRepository;
    private final ProjectSettingsRepository projectSettingsRepository;
    private final boolean signatureRequired;

    private final Cache<Long, Boolean> consentByProject = Caffeine.newBuilder()
            .expireAfterWrite(CONSENT_TTL)
            .maximumSize(MAX_PROJECTS)
            .build();

    public FileAccessGuard(FileUrlSigner signer,
                           ProjectRepository projectRepository,
                           ProjectSettingsRepository projectSettingsRepository,
                           @Value("${file.signed-url.required:true}") boolean signatureRequired) {
        this.signer = signer;
        this.projectRepository = projectRepository;
        this.projectSettingsRepository = projectSettingsRepository;
        this.signatureRequired = signatureRequired;
    }

    /** 내려줘도 되면 그냥 돌아오고, 아니면 {@link FileAccessDeniedException}(403). */
    public void check(String filePath, Long projectId, Long exp, String sig) {
        boolean unsigned = projectId == null && exp == null && !StringUtils.hasText(sig);
        if (unsigned) {
            if (signatureRequired) throw new FileAccessDeniedException();
            return;
        }
        if (projectId == null || exp == null || !signer.verify(filePath, projectId, exp, sig)) {
            throw new FileAccessDeniedException();
        }
        if (!consentEnabled(projectId)) {
            throw new FileAccessDeniedException();
        }
    }

    private boolean consentEnabled(long projectId) {
        if (consentByProject.getIfPresent(projectId) != null) {
            return true;
        }
        boolean enabled = projectRepository.findByIdAndIsDeletedFalse(projectId)
                .flatMap(projectSettingsRepository::findByProject)
                .map(ProjectSettings::getConsentEnabled)
                .map(Boolean.TRUE::equals)
                .orElse(false);
        if (enabled) {
            consentByProject.put(projectId, Boolean.TRUE);
        }
        return enabled;
    }
}
