package com.donatodev.bcm_backend.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Generates a per-user, token-authenticated .ics feed of active contract
 * expirations. Calendar apps subscribe by URL and cannot send a bearer
 * token, so the feed is protected by a long random token embedded in the
 * URL itself instead of the normal JWT flow.
 */
@Service
public class CalendarFeedService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ICS_DATETIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final UsersRepository usersRepository;
    private final ContractsRepository contractsRepository;
    private final CurrentUserResolver currentUserResolver;

    @Value("${app.backend-base-url:http://localhost:8090/api/v1}")
    private String backendBaseUrl;

    public CalendarFeedService(UsersRepository usersRepository, ContractsRepository contractsRepository,
                                CurrentUserResolver currentUserResolver) {
        this.usersRepository = usersRepository;
        this.contractsRepository = contractsRepository;
        this.currentUserResolver = currentUserResolver;
    }

    @Transactional
    public String getOrCreateFeedUrl() {
        Users user = currentUserResolver.resolve();
        if (user.getCalendarToken() == null) {
            user.setCalendarToken(generateToken());
            usersRepository.save(user);
        }
        return buildUrl(user.getCalendarToken());
    }

    @Transactional
    public String regenerateFeedUrl() {
        Users user = currentUserResolver.resolve();
        user.setCalendarToken(generateToken());
        usersRepository.save(user);
        return buildUrl(user.getCalendarToken());
    }

    @Transactional(readOnly = true)
    public String buildIcsFeed(String token) {
        Users user = usersRepository.findByCalendarToken(token)
                .orElseThrow(() -> new UserNotFoundException("Token feed calendario non valido"));

        List<Contracts> contracts = resolveContracts(user);
        String dtstamp = ICS_DATETIME.format(Instant.now());

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n")
                .append("VERSION:2.0\r\n")
                .append("PRODID:-//BCM//Contract Expirations//IT\r\n")
                .append("CALSCALE:GREGORIAN\r\n")
                .append("METHOD:PUBLISH\r\n")
                .append("X-WR-CALNAME:").append(escapeIcsText("Scadenze contratti BCM")).append("\r\n");

        for (Contracts contract : contracts) {
            if (contract.getEndDate() == null) {
                continue;
            }
            String summary = "Scadenza contratto " + contract.getContractNumber() + " - " + contract.getCustomerName();
            ics.append("BEGIN:VEVENT\r\n")
                    .append("UID:contract-").append(contract.getId()).append("@bcm\r\n")
                    .append("DTSTAMP:").append(dtstamp).append("\r\n")
                    .append("DTSTART;VALUE=DATE:").append(contract.getEndDate().format(ICS_DATE)).append("\r\n")
                    .append("DTEND;VALUE=DATE:").append(contract.getEndDate().plusDays(1).format(ICS_DATE)).append("\r\n")
                    .append("SUMMARY:").append(escapeIcsText(summary)).append("\r\n")
                    .append("END:VEVENT\r\n");
        }

        ics.append("END:VCALENDAR\r\n");
        return ics.toString();
    }

    private List<Contracts> resolveContracts(Users user) {
        String role = user.getRole().getRole();
        if (ROLE_ADMIN.equals(role)) {
            Long orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;
            return orgId != null
                    ? contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, orgId)
                    : contractsRepository.findByStatus(ContractStatus.ACTIVE);
        }
        if (user.getManager() != null) {
            return contractsRepository.findByManagerIdAndStatus(user.getManager().getId(), ContractStatus.ACTIVE);
        }
        return List.of();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String buildUrl(String token) {
        return backendBaseUrl + "/calendar/" + token + ".ics";
    }

    private String escapeIcsText(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }
}
