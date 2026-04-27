package ai.univs.auth.support.mail;

import ai.univs.auth.shared.exception.CustomAuthServiceException;
import ai.univs.auth.shared.web.enums.ErrorType;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.smtp.SMTPTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final TemplateEngine templateEngine;

    @Value("${mail.enabled}")
    private boolean mailEnabled;

    @Value("${google.oauth2.email}")
    private String email;

    @Value("${google.oauth2.client-id}")
    private String clientId;

    @Value("${google.oauth2.client-secret}")
    private String clientSecret;

    @Value("${google.oauth2.refresh-token}")
    private String refreshToken;

    @Value("${google.oauth2.access-token-url}")
    private String accessTokenUrl;

    @Async
    public void send(String to,
                     String subject,
                     String templateName,
                     Map<String, Object> variables,
                     Locale locale
    ) {
        try {
            if (!mailEnabled) {
                log.info("The function of email is been inactive.");
                return;
            }

            Context context = new Context();
            context.setLocale(locale);
            variables.forEach(context::setVariable);
            String body = templateEngine.process(templateName, context);

            String accessToken = getAccessToken();

            Properties props = new Properties();
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.auth.mechanisms", "XOAUTH2");

            Session session = Session.getInstance(props);
            MimeMessage message = new MimeMessage(session);
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);

            SMTPTransport smtpTransport = connectToSmtp("smtp.gmail.com", 587, email, accessToken, true);
            smtpTransport.sendMessage(message, message.getAllRecipients());
            smtpTransport.close();

        } catch (Exception e) {
            log.error("Mail send failed: {}", e.getMessage(), e);
            throw new CustomAuthServiceException(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    public String getAccessToken() {
        WebClient webClient = WebClient.create();

        Map<String, String> formData = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "refresh_token", refreshToken,
                "grant_type", "refresh_token"
        );

        Map<String, Object> tokenResponse = (Map<String, Object>) webClient.post()
                .uri(accessTokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining("&")))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {})
                .block();

        return tokenResponse != null ? (String) tokenResponse.get("access_token") : null;
    }

    public SMTPTransport connectToSmtp(String host, int port, String userEmail, String accessToken, boolean debug) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.sasl.enable", "false");

        Session session = Session.getInstance(props);
        session.setDebug(debug);

        SMTPTransport transport = new SMTPTransport(session, new URLName("smtp", host, port, "", "", ""));
        transport.connect(host, port, userEmail, null);

        byte[] response = String.format("user=%s\1auth=Bearer %s\1\1", userEmail, accessToken).getBytes("UTF-8");
        transport.issueCommand("AUTH XOAUTH2 " + Base64.getEncoder().encodeToString(response), 235);

        return transport;
    }
}
