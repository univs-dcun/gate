package ai.univs.gate.shared.utils;

import org.springframework.util.StringUtils;

import java.util.UUID;

public class TransactionUtil {

    public static String useOrCreate(String transactionUuid) {
        return StringUtils.hasText(transactionUuid)
                ? transactionUuid
                : UUID.randomUUID().toString();
    }
}
