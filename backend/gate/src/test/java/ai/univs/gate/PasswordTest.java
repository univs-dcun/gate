package ai.univs.gate;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean matches = encoder.matches("87654321", "$2a$10$Wr2hi0ZFaEvtWi.buSJgAuiqqUqNJiEyk94Nh7KPIKEOg54PIaoTC");
        System.out.println(matches);
    }
}
