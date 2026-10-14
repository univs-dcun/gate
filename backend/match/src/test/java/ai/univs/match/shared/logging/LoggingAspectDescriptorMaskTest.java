package ai.univs.match.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요청 로그에 특징점(descriptor) 원본이 남지 않는다 (UG-382).
 *
 * <p>gate·face 는 UG-279 에서 가림 목록에 descriptor 를 넣었는데 match 만 빠져 있었다. 한 DTO 만 보는
 * 테스트는 새 요청 DTO 가 생기면 아무것도 지키지 못하므로, match 컨트롤러가 받는 <b>모든</b> 요청 DTO 를
 * 찾아 descriptor 가 들어간 필드마다 표식 값을 넣고 로그에 그 값이 없는지 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("match 요청 로그의 descriptor 가림 (UG-382)")
class LoggingAspectDescriptorMaskTest {

    private static final String MARK = "MARK-DESCRIPTOR-";

    @Mock private JoinPoint joinPoint;
    @Mock private MethodSignature signature;

    private LoggingAspect aspect;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        aspect = new LoggingAspect();
        ReflectionTestUtils.setField(aspect, "request", new MockHttpServletRequest("POST", "/api/v1/match"));
        given(joinPoint.getSignature()).willReturn(signature);
        given(signature.getDeclaringTypeName()).willReturn("MatcherController");

        logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("컨트롤러가 받는 요청 DTO 의 descriptor 필드는 모두 [PROTECTED] 로 남는다")
    void 모든_요청_DTO의_descriptor가_가려진다() throws Exception {
        List<Method> endpoints = controllerMethodsWithBody();
        assertThat(endpoints).as("match 컨트롤러에서 요청 DTO 를 받는 메서드를 찾지 못했다").isNotEmpty();

        int descriptorFields = 0;
        for (Method method : endpoints) {
            Object[] args = new Object[method.getParameterCount()];
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length; i++) {
                if (isBody(params[i]) && params[i].getType().isRecord()) {
                    args[i] = recordWithMarks(params[i].getType());
                    descriptorFields += descriptorComponentCount(params[i].getType());
                }
            }
            given(signature.getMethod()).willReturn(method);
            given(signature.getName()).willReturn(method.getName());
            given(joinPoint.getArgs()).willReturn(args);

            aspect.logRequest(joinPoint);
        }

        assertThat(descriptorFields)
                .as("descriptor 필드를 가진 요청 DTO 가 하나도 없다면 이 테스트가 아무것도 지키지 않는다")
                .isPositive();
        String logged = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertThat(logged).doesNotContain(MARK);
        assertThat(logged).contains("[PROTECTED]");
    }

    private static List<Method> controllerMethodsWithBody() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Method> methods = new ArrayList<>();
        for (BeanDefinition bean : scanner.findCandidateComponents("ai.univs.match")) {
            for (Method method : Class.forName(bean.getBeanClassName()).getDeclaredMethods()) {
                for (Parameter p : method.getParameters()) {
                    if (isBody(p) && p.getType().isRecord()) {
                        methods.add(method);
                        break;
                    }
                }
            }
        }
        return methods;
    }

    private static boolean isBody(Parameter p) {
        return p.isAnnotationPresent(RequestBody.class) || p.isAnnotationPresent(ModelAttribute.class);
    }

    private static int descriptorComponentCount(Class<?> type) {
        int n = 0;
        for (RecordComponent c : type.getRecordComponents()) {
            if (c.getName().toLowerCase().contains("descriptor")) {
                n++;
            }
        }
        return n;
    }

    /** 문자열 필드에는 이름이 박힌 표식 값을 넣는다 — descriptor 필드의 표식이 로그에 보이면 실패다. */
    private static Object recordWithMarks(Class<?> type) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            String name = components[i].getName();
            if (types[i] == String.class) {
                values[i] = name.toLowerCase().contains("descriptor") ? MARK + name : "value-" + name;
            } else if (types[i] == Integer.class || types[i] == int.class) {
                values[i] = 1;
            }
        }
        var constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }
}
